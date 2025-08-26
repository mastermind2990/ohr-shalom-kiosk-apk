// Ohr Shalom Donation Kiosk - Android WebView Integration
class OhrShalomKiosk {
    constructor() {
        // Configuration with Davenport, FL defaults
        this.config = {
            adminPin: '12345',
            // Davenport, FL coordinates (as requested)
            latitude: 28.1611,
            longitude: -81.6029,
            timeZone: 'America/New_York',
            // Location configuration - default to Davenport, FL using Geoname ID
            geonameId: 4154279, // Davenport, FL
            locationMethod: 'geoname', // 'geoname' or 'coordinates'
            // Prayer times - defaults
            shacharit: '7:00 AM',
            mincha: '2:00 PM',
            maariv: '8:00 PM',
            organizationName: 'Ohr Shalom'
        }
        
        // State
        this.selectedAmount = 0
        this.tapCount = 0
        this.tapTimeout = null
        
        this.init()
    }
    
    async init() {
        console.log('Initializing Ohr Shalom Kiosk for Android...')
        
        // Load saved configuration first
        this.loadConfigurationFromStorage()
        
        this.setupEventListeners()
        this.loadHebrewCalendar()
        this.updateDateTime()
        this.updatePrayerTimesDisplay()
        
        // Update time every second for current time, every minute for date
        setInterval(() => this.updateDateTime(), 1000)
        
        // Check if Android interface is available and enable kiosk mode
        this.checkAndroidInterface()
        
        // Enable kiosk mode by default
        setTimeout(() => {
            this.enterKioskMode()
        }, 2000) // Wait 2 seconds for initialization to complete
    }
    
    checkAndroidInterface() {
        if (window.AndroidInterface) {
            console.log('Android interface is available')
            this.showMessage('Kiosk ready - Android NFC available', 'success')
        } else {
            console.log('Android interface not available - running in web mode')
            this.showMessage('Web mode - NFC not available', 'info')
        }
    }
    
    loadConfigurationFromStorage() {
        // Try to load from Android first, then fallback to localStorage
        let saved = null
        
        if (window.AndroidInterface && window.AndroidInterface.getConfig) {
            try {
                const androidConfig = window.AndroidInterface.getConfig()
                if (androidConfig) {
                    saved = JSON.parse(androidConfig)
                    console.log('Loaded configuration from Android:', saved)
                }
            } catch (error) {
                console.error('Error loading Android configuration:', error)
            }
        }
        
        // Fallback to localStorage
        if (!saved) {
            const localSaved = localStorage.getItem('ohrShalomKioskConfig')
            if (localSaved) {
                try {
                    saved = JSON.parse(localSaved)
                    console.log('Loaded configuration from localStorage:', saved)
                } catch (error) {
                    console.error('Error loading localStorage configuration:', error)
                }
            }
        }
        
        // Merge with defaults
        if (saved) {
            this.config = { ...this.config, ...saved }
        }
    }
    
    setupEventListeners() {
        // Logo tap for admin access
        const logoContainer = document.getElementById('logoContainer')
        if (logoContainer) {
            logoContainer.addEventListener('click', (e) => {
                e.preventDefault()
                this.handleLogoTap()
            })
        }
        
        // Amount selection buttons
        document.querySelectorAll('.amount-button[data-amount]').forEach(button => {
            button.addEventListener('click', (e) => {
                const amount = parseFloat(e.currentTarget.dataset.amount)
                this.setAmount(amount)
            })
        })
        
        // Custom amount button
        document.getElementById('customAmountBtn').addEventListener('click', () => {
            this.showCustomAmountModal()
        })
        
        // Payment method buttons
        document.getElementById('tapToPayBtn').addEventListener('click', () => {
            this.startTapToPay()
        })
        
        // Modal handlers
        this.setupModalHandlers()
        
        // Prevent right-click and other kiosk optimizations
        document.addEventListener('contextmenu', e => e.preventDefault())
        document.addEventListener('selectstart', e => e.preventDefault())
        
        // Disable certain keyboard shortcuts
        document.addEventListener('keydown', (e) => {
            if (e.ctrlKey || e.altKey || e.metaKey) {
                if (['r', 'f5', 'f11', 'f12', 'i', 'j', 'u', 's'].includes(e.key.toLowerCase())) {
                    e.preventDefault()
                }
            }
        })
        
        // Global keypress listener for admin PIN entry
        this.adminPinSequence = ''
        this.adminPinTimeout = null
        
        // Try both keypress and keydown for better compatibility
        document.addEventListener('keypress', this.handleAdminPinEntry.bind(this))
        document.addEventListener('keydown', this.handleAdminPinEntry.bind(this))
        
        // Also add a global function for debugging
        window.debugAdminModal = () => {
            console.log('Debug: manually triggering admin modal')
            this.showAdminModal()
        }
        
        window.debugPinEntry = (pin) => {
            console.log('Debug: manually setting PIN sequence to:', pin)
            this.adminPinSequence = pin || '12345'
            if (this.adminPinSequence === this.config.adminPin) {
                console.log('Debug: PIN matches, showing modal')
                this.showAdminModal()
            }
        }
    }
    
    setupModalHandlers() {
        // Admin modal
        document.getElementById('adminSubmit').addEventListener('click', () => {
            this.checkAdminPin()
        })
        
        document.getElementById('adminCancel').addEventListener('click', () => {
            this.hideAdminModal()
        })
        
        document.getElementById('adminPinInput').addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                this.checkAdminPin()
            }
        })
        
        // Custom amount modal
        document.getElementById('customAmountSubmit').addEventListener('click', () => {
            this.setCustomAmount()
        })
        
        document.getElementById('customAmountCancel').addEventListener('click', () => {
            this.hideCustomAmountModal()
        })
        
        document.getElementById('customAmountInput').addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                this.setCustomAmount()
            }
        })
        
        // Cancel tap to pay
        document.getElementById('cancelTapToPay').addEventListener('click', () => {
            this.cancelTapToPay()
        })
    }
    
    handleLogoTap() {
        this.tapCount++
        console.log('Logo tapped, count:', this.tapCount)
        
        // Show visual feedback
        this.showMessage(`Tap ${this.tapCount}/5 for admin access`, 'info', 1000)
        
        if (this.tapTimeout) {
            clearTimeout(this.tapTimeout)
        }
        
        this.tapTimeout = setTimeout(() => {
            this.tapCount = 0
        }, 3000)
        
        if (this.tapCount >= 5) {
            this.tapCount = 0
            this.showAdminModal()
        }
    }
    
    handleAdminPinEntry(e) {
        // Prevent duplicate handling of same event
        if (e.type === 'keydown' && this.lastKeyDownTime && (Date.now() - this.lastKeyDownTime) < 50) {
            return
        }
        if (e.type === 'keypress' && this.lastKeyPressTime && (Date.now() - this.lastKeyPressTime) < 50) {
            return  
        }
        
        this.lastKeyDownTime = e.type === 'keydown' ? Date.now() : this.lastKeyDownTime
        this.lastKeyPressTime = e.type === 'keypress' ? Date.now() : this.lastKeyPressTime
        
        console.log('Key event:', e.type, 'Key:', e.key, 'Target:', e.target.tagName, 'Current sequence:', this.adminPinSequence)
        
        // Only handle numeric keys and ignore if we're in an input field
        if (e.target.tagName === 'INPUT') {
            console.log('Ignoring key event in input field')
            return
        }
        
        const key = e.key
        if (!key.match(/[0-9]/)) {
            console.log('Non-numeric key ignored:', key)
            return
        }
        
        // Only process on keydown to avoid duplicates
        if (e.type !== 'keydown') {
            return
        }
        
        // Add to PIN sequence
        this.adminPinSequence += key
        console.log('PIN sequence updated:', this.adminPinSequence.replace(/./g, '*'), 'Length:', this.adminPinSequence.length)
        console.log('Expected PIN:', this.config.adminPin, 'Current matches:', this.adminPinSequence === this.config.adminPin)
        
        // Clear timeout and set new one
        if (this.adminPinTimeout) {
            clearTimeout(this.adminPinTimeout)
        }
        
        this.adminPinTimeout = setTimeout(() => {
            console.log('PIN sequence timeout - resetting')
            this.adminPinSequence = ''
        }, 5000) // Increased timeout to 5 seconds
        
        // Check if PIN matches
        if (this.adminPinSequence === this.config.adminPin) {
            this.adminPinSequence = ''
            console.log('=== Admin PIN MATCHED! Showing modal... ===')
            this.showAdminModal()
        } else if (this.adminPinSequence.length >= this.config.adminPin.length) {
            // Wrong PIN - reset
            console.log('Wrong PIN entered, resetting. Expected:', this.config.adminPin, 'Got:', this.adminPinSequence)
            this.adminPinSequence = ''
            this.showMessage('Invalid PIN - try typing 12345', 'error', 3000)
        } else if (this.adminPinSequence.length === 1) {
            // Give user feedback that PIN entry has started
            this.showMessage(`PIN entry started (${this.adminPinSequence.length}/${this.config.adminPin.length})`, 'info', 1000)
        }
    }
    
    setAmount(amount) {
        this.selectedAmount = amount
        
        // Update amount displays
        document.getElementById('selectedAmount').textContent = `$${amount.toFixed(2)}`
        document.getElementById('tapAmount').textContent = `$${amount.toFixed(2)}`
        
        // Add Hebrew equivalent for special amounts
        const hebrewAmountEl = document.getElementById('selectedAmountHebrew')
        if (amount === 18) {
            hebrewAmountEl.textContent = 'חי (Chai - Life)'
        } else if (amount === 36) {
            hebrewAmountEl.textContent = 'Double חי (Double Life)'
        } else {
            hebrewAmountEl.textContent = ''
        }
        
        // Add animation to amount container
        const amountContainer = document.getElementById('selectedAmountContainer')
        amountContainer.classList.remove('bounce-in')
        setTimeout(() => amountContainer.classList.add('bounce-in'), 10)
        
        // Highlight selected button
        document.querySelectorAll('.amount-button').forEach(btn => {
            btn.classList.remove('ring-4', 'ring-yellow-400', 'success-glow')
        })
        
        const selectedBtn = document.querySelector(`[data-amount="${amount}"]`)
        if (selectedBtn) {
            selectedBtn.classList.add('ring-4', 'ring-yellow-400', 'success-glow')
        }
        
        this.showMessage(`Selected donation: $${amount.toFixed(2)}`, 'success', 2000)
    }
    
    async startTapToPay() {
        if (this.selectedAmount <= 0) {
            this.showMessage('Please select an amount first', 'error')
            return
        }
        
        try {
            const email = document.getElementById('emailInput').value.trim()
            
            // Show tap to pay interface
            document.getElementById('tapToPayInterface').classList.remove('hidden')
            document.getElementById('tapToPayInterface').classList.add('slide-up')
            
            // Check if Android interface is available for real NFC payments
            if (window.AndroidInterface && window.AndroidInterface.processNfcPayment) {
                await this.processAndroidNfcPayment(this.selectedAmount, email)
            } else {
                // Fallback to demo payment for web testing
                console.log('Android NFC not available, using demo mode')
                await this.processDemoPayment(this.selectedAmount, email)
            }
        } catch (error) {
            console.error('Tap to Pay error:', error)
            this.showMessage('Tap to Pay failed: ' + error.message, 'error')
            document.getElementById('tapToPayInterface').classList.add('hidden')
        }
    }
    
    async processAndroidNfcPayment(amountDollars, email) {
        try {
            this.showMessage('Initializing NFC payment system...', 'info')
            
            // Convert dollars to cents for Stripe
            const amountCents = Math.round(amountDollars * 100)
            
            console.log(`Processing Android NFC payment: $${amountDollars} (${amountCents} cents)`)
            
            // Call Android interface to process NFC payment
            const paymentData = {
                amount: amountCents,
                currency: 'usd',
                email: email || null
            }
            
            // Call Android method to start NFC payment
            this.showMessage('Ready for NFC payment - Please tap your card or device', 'info')
            
            const result = window.AndroidInterface.processNfcPayment(JSON.stringify(paymentData))
            
            // Android will handle the NFC interaction and return result
            if (result === 'success') {
                this.showMessage('Payment successful! Thank you for your donation', 'success')
                
                // Show success interface
                setTimeout(() => {
                    this.showSuccessInterface(amountDollars)
                }, 1000)
                
                // Reset interface after delay
                setTimeout(() => {
                    this.resetInterface()
                }, 5000)
            } else {
                throw new Error('Payment was not completed or failed')
            }
            
        } catch (error) {
            console.error('Android NFC payment error:', error)
            this.showMessage(`NFC Payment failed: ${error.message}`, 'error')
            throw error
        }
    }
    
    async processDemoPayment(amount, email) {
        try {
            console.log('Processing demo payment (simulated) for $' + amount)
            this.showMessage('Demo Mode: Ready for Tap to Pay', 'info')
            
            // Simulate processing
            setTimeout(() => {
                document.getElementById('tapToPayInterface').classList.add('hidden')
                document.getElementById('processingInterface').classList.remove('hidden')
                document.getElementById('processingInterface').classList.add('slide-up')
                this.showMessage('Processing your donation...', 'info')
            }, 2000)
            
            // Complete processing
            const processingTime = 4000 + Math.random() * 2000 // 4-6 seconds total
            
            setTimeout(() => {
                if (Math.random() > 0.05) { // 95% success rate
                    this.showSuccessInterface(amount)
                    this.showMessage('Payment successful! Thank you for your generosity', 'success')
                    
                    // Reset interface after showing success
                    setTimeout(() => {
                        this.resetInterface()
                    }, 5000)
                } else {
                    document.getElementById('processingInterface').classList.add('hidden')
                    this.showMessage('Payment declined - please try again', 'error')
                    this.resetInterface()
                }
            }, processingTime)
            
        } catch (error) {
            console.error('Demo payment error:', error)
            this.showMessage(`Demo payment failed: ${error.message}`, 'error')
            this.resetInterface()
            throw error
        }
    }
    
    showSuccessInterface(amount) {
        document.getElementById('processingInterface').classList.add('hidden')
        document.getElementById('successInterface').classList.remove('hidden')
        document.getElementById('successInterface').classList.add('bounce-in')
        document.getElementById('successAmount').textContent = `$${amount.toFixed(2)}`
    }
    
    cancelTapToPay() {
        document.getElementById('tapToPayInterface').classList.add('hidden')
        this.showMessage('Tap to Pay cancelled', 'info')
    }
    
    resetInterface() {
        this.selectedAmount = 0
        
        // Reset amount displays
        document.getElementById('selectedAmount').textContent = '$0.00'
        document.getElementById('tapAmount').textContent = '$0.00'
        document.getElementById('selectedAmountHebrew').textContent = ''
        
        // Clear email input
        document.getElementById('emailInput').value = ''
        
        // Hide all payment interfaces
        document.getElementById('tapToPayInterface').classList.add('hidden')
        document.getElementById('processingInterface').classList.add('hidden')
        document.getElementById('successInterface').classList.add('hidden')
        
        // Remove all animations and highlights
        document.querySelectorAll('.amount-button').forEach(btn => {
            btn.classList.remove('ring-4', 'ring-yellow-400', 'success-glow')
        })
        
        // Remove animation classes from containers
        const containers = ['selectedAmountContainer', 'tapToPayInterface', 'processingInterface', 'successInterface']
        containers.forEach(containerId => {
            const element = document.getElementById(containerId)
            if (element) {
                element.classList.remove('bounce-in', 'slide-up', 'success-glow')
            }
        })
        
        console.log('Interface reset to initial state')
    }
    
    showAdminModal() {
        console.log('=== showAdminModal() called ===')
        const modal = document.getElementById('adminModal')
        console.log('Admin modal element:', modal)
        
        if (modal) {
            console.log('Modal current classes:', modal.className)
            modal.classList.remove('hidden')
            console.log('Modal classes after removing hidden:', modal.className)
            console.log('Admin modal should now be visible')
            
            const pinInput = document.getElementById('adminPinInput')
            console.log('PIN input element:', pinInput)
            if (pinInput) {
                setTimeout(() => {
                    pinInput.focus()
                    console.log('PIN input focused')
                }, 100)
            }
        } else {
            console.error('Admin modal element not found!')
            // Let's check if any element with adminModal exists
            const allElements = document.querySelectorAll('*[id*="admin"]')
            console.log('All elements with "admin" in ID:', allElements)
        }
    }
    
    hideAdminModal() {
        document.getElementById('adminModal').classList.add('hidden')
        document.getElementById('adminPinInput').value = ''
    }
    
    checkAdminPin() {
        const pin = document.getElementById('adminPinInput').value
        if (pin === this.config.adminPin) {
            this.hideAdminModal()
            this.showAdminConfig()
        } else {
            this.showMessage('Invalid PIN', 'error')
            document.getElementById('adminPinInput').value = ''
        }
    }
    
    showAdminConfig() {
        // Call Android interface to show admin configuration
        if (window.AndroidInterface && window.AndroidInterface.showAdminConfig) {
            window.AndroidInterface.showAdminConfig()
        } else {
            // Web fallback - show basic config modal
            this.showMessage('Admin mode - configuration available in Android app', 'info')
        }
    }
    
    showCustomAmountModal() {
        document.getElementById('customAmountModal').classList.remove('hidden')
        document.getElementById('customAmountInput').focus()
    }
    
    hideCustomAmountModal() {
        document.getElementById('customAmountModal').classList.add('hidden')
        document.getElementById('customAmountInput').value = ''
    }
    
    setCustomAmount() {
        const amount = parseFloat(document.getElementById('customAmountInput').value)
        if (isNaN(amount) || amount <= 0) {
            this.showMessage('Please enter a valid amount', 'error')
            return
        }
        
        this.setAmount(amount)
        this.hideCustomAmountModal()
    }
    
    async loadHebrewCalendar() {
        try {
            console.log('Loading Hebrew calendar...')
            
            // Always use web API for reliable data loading
            await this.loadHebrewCalendarFromWeb()
            
            // Also trigger Android background loading for future caching
            if (window.AndroidInterface && window.AndroidInterface.getHebrewCalendar) {
                window.AndroidInterface.getHebrewCalendar()
            }
            
        } catch (error) {
            console.error('Failed to load Hebrew calendar:', error)
            this.setCalendarErrorStates()
        }
    }
    
    async loadHebrewCalendarFromWeb() {
        try {
            // Use Davenport, FL coordinates by default (geoname seems to have issues)
            const latitude = this.config.latitude || 28.1611
            const longitude = this.config.longitude || -81.6029
            
            // Always use coordinates as they seem more reliable
            const url = `https://www.hebcal.com/shabbat?cfg=json&m=50&latitude=${latitude}&longitude=${longitude}`
            
            console.log('Hebrew calendar API URL:', url)
            
            const response = await fetch(url)
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`)
            }
            
            const data = await response.json()
            console.log('Hebrew calendar API response:', data)
            
            // Validate the response structure
            if (!data || !data.items || !Array.isArray(data.items)) {
                throw new Error('Invalid API response structure')
            }
            
            if (data.items.length === 0) {
                throw new Error('No items returned from HebCal API')
            }
            
            this.displayHebrewCalendar(data)
        } catch (error) {
            console.error('Failed to load Hebrew calendar from web:', error)
            this.setCalendarErrorStates()
        }
    }
    
    setCalendarErrorStates() {
        // Set fallback error text for all Shabbat time elements
        const sabbatElements = ['candleLighting', 'eighteenMin', 'havdalah', 'seventytwoMin']
        sabbatElements.forEach(elementId => {
            const element = document.getElementById(elementId)
            if (element) {
                element.textContent = 'Unavailable'
            }
        })
        
        // Also set fallback Hebrew date and parsha
        const hebrewDateEl = document.getElementById('hebrewDate')
        if (hebrewDateEl) hebrewDateEl.textContent = 'Unavailable'
        
        const parshaEl = document.getElementById('parsha')
        if (parshaEl) parshaEl.textContent = 'Unavailable'
    }
    
    displayHebrewCalendar(data) {
        console.log('Displaying Hebrew calendar data:', data)
        const items = data.items || []
        
        if (!items.length) {
            console.error('No items in Hebrew calendar data')
            this.setCalendarErrorStates()
            return
        }
        
        // Find parsha - category should be 'parashat'
        const parsha = items.find(item => item.category === 'parashat')
        console.log('Found parsha:', parsha)
        
        if (parsha) {
            // Display Hebrew date from parsha
            if (parsha.hdate) {
                const hebrewDateEl = document.getElementById('hebrewDate')
                if (hebrewDateEl) {
                    hebrewDateEl.textContent = parsha.hdate
                    console.log('Set Hebrew date:', parsha.hdate)
                }
            }
            
            // Display parsha name - prefer Hebrew, fallback to English
            const parshaEl = document.getElementById('parsha')
            if (parshaEl) {
                const parshaText = parsha.hebrew || parsha.title || 'No Parsha'
                parshaEl.textContent = parshaText
                console.log('Set parsha:', parshaText)
            }
        } else {
            console.error('No parsha found in API response')
            const hebrewDateEl = document.getElementById('hebrewDate')
            if (hebrewDateEl) hebrewDateEl.textContent = 'No Hebrew Date'
            const parshaEl = document.getElementById('parsha')
            if (parshaEl) parshaEl.textContent = 'No Parsha'
        }
        
        // Find candle lighting - category should be 'candles'
        const candles = items.find(item => item.category === 'candles')
        console.log('Found candle lighting:', candles)
        
        const candleElement = document.getElementById('candleLighting')
        if (candleElement) {
            if (candles) {
                // Extract time from title like "Candle lighting: 7:32pm"
                const timeMatch = candles.title.match(/(\d{1,2}:\d{2}[ap]m)/i)
                const timeText = timeMatch ? timeMatch[1] : 'Invalid time'
                candleElement.textContent = timeText
                console.log('Set candle lighting:', timeText)
            } else {
                candleElement.textContent = 'No candles'
                console.error('No candle lighting found in API response')
            }
        }
        
        // Find Havdalah - category should be 'havdalah'
        const havdalah = items.find(item => item.category === 'havdalah')
        console.log('Found havdalah:', havdalah)
        
        const havdalahElement = document.getElementById('havdalah')
        if (havdalahElement) {
            if (havdalah) {
                // Extract time from title like "Havdalah (50 min): 8:39pm"
                const timeMatch = havdalah.title.match(/(\d{1,2}:\d{2}[ap]m)/i)
                const timeText = timeMatch ? timeMatch[1] : 'Invalid time'
                havdalahElement.textContent = timeText
                console.log('Set havdalah:', timeText)
            } else {
                havdalahElement.textContent = 'No Havdalah'
                console.error('No havdalah found in API response')
            }
        }
        
        // Calculate 18 min and 72 min times based on candle lighting and Havdalah
        this.calculateSabbathTimes(candles, havdalah)
    }
    
    calculateSabbathTimes(candles, havdalah) {
        if (candles || havdalah) {
            let sunsetTime = null
            
            if (candles) {
                const candleTimeMatch = candles.title.match(/(\d{1,2}):(\d{2})\s*([ap]m)/i)
                if (candleTimeMatch) {
                    let candleHours = parseInt(candleTimeMatch[1])
                    const candleMinutes = parseInt(candleTimeMatch[2])
                    const period = candleTimeMatch[3].toLowerCase()
                    
                    if (period === 'pm' && candleHours !== 12) candleHours += 12
                    if (period === 'am' && candleHours === 12) candleHours = 0
                    
                    const candleDate = new Date()
                    candleDate.setHours(candleHours, candleMinutes, 0, 0)
                    
                    // Calculate sunset (candle lighting + 18 minutes)
                    sunsetTime = new Date(candleDate.getTime() + 18 * 60 * 1000)
                }
            }
            
            if (!sunsetTime && havdalah) {
                const havdalahTimeMatch = havdalah.title.match(/(\d{1,2}):(\d{2})\s*([ap]m)/i)
                if (havdalahTimeMatch) {
                    let havdalahHours = parseInt(havdalahTimeMatch[1])
                    const havdalahMinutes = parseInt(havdalahTimeMatch[2])
                    const period = havdalahTimeMatch[3].toLowerCase()
                    
                    if (period === 'pm' && havdalahHours !== 12) havdalahHours += 12
                    if (period === 'am' && havdalahHours === 12) havdalahHours = 0
                    
                    const havdalahDate = new Date()
                    havdalahDate.setHours(havdalahHours, havdalahMinutes, 0, 0)
                    
                    // Estimate sunset (Havdalah - 50 minutes)
                    sunsetTime = new Date(havdalahDate.getTime() - 50 * 60 * 1000)
                }
            }
            
            if (sunsetTime) {
                // Calculate 18 minutes after sunset
                const eighteenMinDate = new Date(sunsetTime.getTime() + 18 * 60 * 1000)
                const eighteenMinTime = eighteenMinDate.toLocaleTimeString('en-US', { 
                    hour: 'numeric', 
                    minute: '2-digit', 
                    hour12: true 
                }).toLowerCase()
                
                // Calculate 72 minutes after sunset
                const seventytwoMinDate = new Date(sunsetTime.getTime() + 72 * 60 * 1000)
                const seventytwoMinTime = seventytwoMinDate.toLocaleTimeString('en-US', { 
                    hour: 'numeric', 
                    minute: '2-digit', 
                    hour12: true 
                }).toLowerCase()
                
                // Update 18 Min
                const eighteenMinElement = document.getElementById('eighteenMin')
                if (eighteenMinElement) {
                    eighteenMinElement.textContent = eighteenMinTime
                }
                
                // Update 72min
                const seventytwoMinElement = document.getElementById('seventytwoMin')
                if (seventytwoMinElement) {
                    seventytwoMinElement.textContent = seventytwoMinTime
                }
            }
        }
    }
    
    updatePrayerTimesDisplay() {
        // Update Shacharit
        const shacharitElement = document.getElementById('shacharit')
        if (shacharitElement) {
            const timeSpan = shacharitElement.querySelector('span.font-bold.text-yellow-700')
            if (timeSpan) {
                timeSpan.textContent = this.config.shacharit || '7:00 AM'
            }
        }
        
        // Update Mincha
        const minchaElement = document.getElementById('mincha')
        if (minchaElement) {
            const timeSpan = minchaElement.querySelector('span.font-bold.text-orange-700')
            if (timeSpan) {
                timeSpan.textContent = this.config.mincha || '2:00 PM'
            }
        }
        
        // Update Maariv
        const maarivElement = document.getElementById('maariv')
        if (maarivElement) {
            const timeSpan = maarivElement.querySelector('span.font-bold.text-indigo-700')
            if (timeSpan) {
                timeSpan.textContent = this.config.maariv || '8:00 PM'
            }
        }
    }
    
    updateDateTime() {
        const now = new Date()
        
        // Update current time display
        const timeOptions = {
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            timeZone: this.config.timeZone,
            hour12: true
        }
        
        const currentTimeEl = document.getElementById('currentTime')
        if (currentTimeEl) {
            currentTimeEl.textContent = now.toLocaleTimeString('en-US', timeOptions)
        }
        
        // Update date display
        const dateOptions = {
            weekday: 'long',
            year: 'numeric',
            month: 'long',
            day: 'numeric',
            timeZone: this.config.timeZone
        }
        
        const gregorianDateEl = document.getElementById('gregorianDate')
        if (gregorianDateEl) {
            gregorianDateEl.textContent = now.toLocaleDateString('en-US', dateOptions)
        }
    }
    
    showMessage(message, type = 'info', duration = 5000) {
        const messageEl = document.getElementById('statusMessage')
        
        // Set styling based on type
        const styles = {
            success: 'bg-green-500 text-white',
            error: 'bg-red-500 text-white',
            info: 'bg-blue-500 text-white',
            warning: 'bg-yellow-500 text-black'
        }
        
        messageEl.className = `px-4 py-2 rounded-lg shadow-lg ${styles[type] || styles.info}`
        messageEl.textContent = message
        messageEl.classList.remove('hidden')
        
        // Auto-hide after duration
        setTimeout(() => {
            messageEl.classList.add('hidden')
        }, duration)
    }
    
    // Android interface methods that can be called from native code
    updateConfig(configJson) {
        try {
            const newConfig = JSON.parse(configJson)
            this.config = { ...this.config, ...newConfig }
            
            // Update displays
            this.updatePrayerTimesDisplay()
            this.loadHebrewCalendar()
            
            console.log('Configuration updated from Android:', this.config)
            this.showMessage('Configuration updated', 'success')
        } catch (error) {
            console.error('Error updating config from Android:', error)
        }
    }
    
    paymentCompleted(success, amount, message) {
        if (success) {
            this.showSuccessInterface(amount / 100) // Convert cents to dollars
            this.showMessage(message || 'Payment successful!', 'success')
            setTimeout(() => this.resetInterface(), 5000)
        } else {
            this.showMessage(message || 'Payment failed', 'error')
            this.resetInterface()
        }
    }
    
    enterKioskMode() {
        if (window.AndroidInterface && window.AndroidInterface.enterKioskMode) {
            window.AndroidInterface.enterKioskMode()
        } else {
            console.log('Kiosk mode not available in web version')
        }
    }
    
    exitKioskMode() {
        if (window.AndroidInterface && window.AndroidInterface.exitKioskMode) {
            window.AndroidInterface.exitKioskMode()
        }
    }
}

// Initialize the kiosk when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    window.kioskInstance = new OhrShalomKiosk()
    console.log('=== Kiosk instance created and available as window.kioskInstance ===')
    console.log('Debug functions available:')
    console.log('- window.debugAdminModal() - Show admin modal directly')
    console.log('- window.debugPinEntry("12345") - Test PIN entry')
    console.log('- window.kioskInstance.showAdminModal() - Direct modal access')
    console.log('Admin PIN is:', window.kioskInstance.config.adminPin)
})

console.log('Ohr Shalom Kiosk JavaScript loaded for Android WebView')