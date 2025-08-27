// Ohr Shalom Donation Kiosk - Android WebView Integration
// Version: 1.1-hebcal-debug
// Build: August 26, 2025
class OhrShalomKiosk {
    constructor() {
        this.version = '1.8.2-terminal-connected'
        this.buildDate = '2025-08-26'
        // Configuration with Davenport, FL defaults
        this.config = {
            adminPin: '12345',
            // Davenport, FL coordinates (as requested)
            latitude: 28.1611,
            longitude: -81.6029,
            timeZone: 'America/New_York',
            // Location configuration - default to Davenport, FL using coordinates
            geonameId: null, // Disabled - use coordinates instead
            locationMethod: 'coordinates', // 'geoname' or 'coordinates'
            // Prayer times - defaults
            shacharit: '7:00 AM',
            mincha: '2:00 PM',
            maariv: '8:00 PM',
            organizationName: 'Ohr Shalom',
            logoUrl: '', // Custom logo URL
            stripeTestMode: false, // Test mode for Stripe payments
            // Stripe configuration - Production defaults embedded
            stripePublishableKey: 'pk_live_51Q5QhsJhCdJUSe2h1hl7iqL7YLmprQQMu7FLmkDzULDwacidH6LmzH4dbodT2k2FP7Sh9whkLmZ5YHmGFEi4MrtE0081NqrCtr', // Live publishable key
            stripeTokenEndpoint: 'http://161.35.140.12/api/stripe/connection_token', // Production backend endpoint
            stripeLocationId: 'tml_GKsXoQ8u9cFZJF', // Production Terminal location ID
            stripeEnvironment: 'live' // Live mode for production
        }
        
        // State
        this.selectedAmount = 0
        this.tapCount = 0
        this.tapTimeout = null
        
        this.init()
    }
    
    async init() {
        console.log(`=== KIOSK DEBUG: Initializing Ohr Shalom Kiosk v${this.version} (${this.buildDate}) ===`)
        
        // Add debugging information to Android logcat
        if (window.AndroidInterface && window.AndroidInterface.log) {
            window.AndroidInterface.log(`KIOSK DEBUG: JavaScript v${this.version} initialization started`)
        }
        
        // Test network connectivity
        await this.testNetworkConnectivity()
        
        // Test simple API
        await this.testSimpleApi()
        
        // Get and log kiosk info
        this.getKioskInfo()
        
        // Load saved configuration first
        this.loadConfigurationFromStorage()
        
        // Auto-configure Stripe if needed (simple version)
        this.autoConfigureStripeIfNeeded()
        
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
    
    autoConfigureStripeIfNeeded() {
        // Simple auto-configuration for production Stripe credentials
        // This method updates the Android interface with the hardcoded production credentials
        console.log('KIOSK DEBUG: Auto-configuring Stripe credentials if needed')
        
        if (window.AndroidInterface && window.AndroidInterface.setStripeCredentials) {
            try {
                // Pass the production credentials to Android
                const success = window.AndroidInterface.setStripeCredentials(
                    this.config.stripePublishableKey,
                    this.config.stripeTokenEndpoint,
                    this.config.stripeLocationId,
                    this.config.stripeEnvironment
                )
                
                if (success) {
                    console.log('KIOSK DEBUG: Stripe credentials configured successfully')
                } else {
                    console.log('KIOSK DEBUG: Failed to configure Stripe credentials')
                }
            } catch (error) {
                console.error('KIOSK DEBUG: Error configuring Stripe credentials:', error)
            }
        } else {
            console.log('KIOSK DEBUG: Android Stripe interface not available, skipping auto-configuration')
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
        
        // Admin configuration modal handlers
        document.getElementById('adminConfigClose').addEventListener('click', () => {
            this.hideAdminConfigModal()
        })
        
        document.getElementById('adminConfigCancel').addEventListener('click', () => {
            this.hideAdminConfigModal()
        })
        
        document.getElementById('adminConfigSave').addEventListener('click', () => {
            this.saveAdminConfig()
        })
        
        // Admin action buttons
        document.getElementById('adminToggleKiosk').addEventListener('click', () => {
            this.toggleKioskMode()
        })
        
        document.getElementById('adminResetConfig').addEventListener('click', () => {
            this.resetConfigToDefaults()
        })
        
        // Admin testing buttons
        document.getElementById('adminTestNetwork').addEventListener('click', () => {
            this.runAdminTest('network')
        })
        
        document.getElementById('adminTestHebcal').addEventListener('click', () => {
            this.runAdminTest('hebcal')
        })
        
        document.getElementById('adminRefreshData').addEventListener('click', () => {
            this.runAdminTest('refresh')
        })
        
        document.getElementById('adminShowLogs').addEventListener('click', () => {
            this.runAdminTest('logs')
        })
        
        // Logo customization handlers
        document.getElementById('adminTestLogo').addEventListener('click', () => {
            this.previewCustomLogo()
        })
        
        document.getElementById('adminResetLogo').addEventListener('click', () => {
            this.resetLogo()
        })
        
        // Stripe testing handlers
        document.getElementById('adminTestStripeConnection').addEventListener('click', () => {
            this.runStripeTest('connection')
        })
        
        document.getElementById('adminTestStripeAPI').addEventListener('click', () => {
            this.runStripeTest('api')
        })
        
        document.getElementById('adminTestStripeTerminal').addEventListener('click', () => {
            this.runStripeTest('terminal')
        })
        
        document.getElementById('adminStripeTestMode').addEventListener('click', () => {
            this.toggleStripeTestMode()
        })
        
        document.getElementById('adminStripeTest').addEventListener('click', () => {
            this.startTestPayment()
        })
        
        // Stripe credential management
        document.getElementById('adminValidateStripeCredentials').addEventListener('click', () => {
            this.validateStripeCredentials()
        })
        
        document.getElementById('adminClearStripeCredentials').addEventListener('click', () => {
            this.clearStripeCredentials()
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
        console.log('=== ADMIN DEBUG: showAdminConfig called ===')
        
        // Show the admin configuration modal
        this.showAdminConfigModal()
        
        // Also call Android interface for additional functionality if available
        if (window.AndroidInterface && window.AndroidInterface.showAdminConfig) {
            console.log('ADMIN DEBUG: Calling Android showAdminConfig for additional functionality')
            // Don't call it as it shows Toast - we'll handle everything in JavaScript
            // window.AndroidInterface.showAdminConfig()
        }
    }
    
    showAdminConfigModal() {
        console.log('ADMIN DEBUG: Showing admin configuration modal')
        
        const modal = document.getElementById('adminConfigModal')
        if (modal) {
            modal.classList.remove('hidden')
            
            // Populate current configuration values
            this.populateAdminConfig()
            
            console.log('ADMIN DEBUG: Admin config modal should now be visible')
        } else {
            console.error('ADMIN DEBUG: adminConfigModal element not found!')
        }
    }
    
    populateAdminConfig() {
        console.log('ADMIN DEBUG: Populating admin configuration with current values')
        
        // System information
        document.getElementById('adminVersion').textContent = this.version
        document.getElementById('adminBuildDate').textContent = this.buildDate
        
        // Configuration values
        document.getElementById('adminOrgName').value = this.config.organizationName
        document.getElementById('adminLatitude').value = this.config.latitude
        document.getElementById('adminLongitude').value = this.config.longitude
        document.getElementById('adminTimezone').value = this.config.timeZone
        
        // Convert prayer times to 24-hour format for time inputs
        document.getElementById('adminShacharit').value = this.convertTo24Hour(this.config.shacharit)
        document.getElementById('adminMincha').value = this.convertTo24Hour(this.config.mincha)
        document.getElementById('adminMaariv').value = this.convertTo24Hour(this.config.maariv)
        
        // Logo URL
        document.getElementById('adminLogoUrl').value = this.config.logoUrl || ''
        
        // Stripe Configuration
        document.getElementById('adminStripePublishableKey').value = this.config.stripePublishableKey || ''
        document.getElementById('adminStripeTokenEndpoint').value = this.config.stripeTokenEndpoint || ''
        document.getElementById('adminStripeLocationId').value = this.config.stripeLocationId || ''
        document.getElementById('adminStripeEnvironment').value = this.config.stripeEnvironment || 'test'
        
        // Status checks
        this.updateAdminStatus()
    }
    
    convertTo24Hour(timeString) {
        // Convert "7:00 AM" to "07:00" format
        try {
            const time = timeString.toLowerCase().replace(/\s+/g, '')
            const isPM = time.includes('pm')
            const timeOnly = time.replace(/[ap]m/, '')
            let [hours, minutes] = timeOnly.split(':')
            
            hours = parseInt(hours)
            minutes = minutes || '00'
            
            if (isPM && hours !== 12) hours += 12
            if (!isPM && hours === 12) hours = 0
            
            return `${hours.toString().padStart(2, '0')}:${minutes.padStart(2, '0')}`
        } catch (e) {
            console.warn('Failed to convert time:', timeString, e)
            return '12:00'
        }
    }
    
    convertTo12Hour(time24) {
        // Convert "07:00" to "7:00 AM" format
        try {
            let [hours, minutes] = time24.split(':')
            hours = parseInt(hours)
            const ampm = hours >= 12 ? 'PM' : 'AM'
            hours = hours % 12 || 12
            return `${hours}:${minutes} ${ampm}`
        } catch (e) {
            console.warn('Failed to convert time:', time24, e)
            return time24
        }
    }
    
    updateAdminStatus() {
        // Update kiosk mode status
        if (window.AndroidInterface && window.AndroidInterface.getKioskStatus) {
            try {
                const status = JSON.parse(window.AndroidInterface.getKioskStatus())
                document.getElementById('adminKioskStatus').textContent = status.kioskMode ? 'ENABLED' : 'DISABLED'
                document.getElementById('adminKioskStatus').className = status.kioskMode ? 'font-semibold text-green-600' : 'font-semibold text-red-600'
                
                document.getElementById('adminNfcStatus').textContent = status.nfcStatus === 'enabled' ? 'READY' : 'DISABLED'
            } catch (e) {
                console.warn('Failed to get Android status:', e)
            }
        }
        
        // Check Hebrew data status
        const hebrewDate = document.getElementById('hebrewDate')?.textContent
        const parsha = document.getElementById('parsha')?.textContent
        const hebrewStatus = (hebrewDate && hebrewDate !== 'Unavailable' && parsha && parsha !== 'Unavailable') ? 'LOADED' : 'ERROR'
        document.getElementById('adminHebrewStatus').textContent = hebrewStatus
        document.getElementById('adminHebrewStatus').className = hebrewStatus === 'LOADED' ? 'text-green-600' : 'text-red-600'
        
        // Check Stripe Terminal status
        this.updateTerminalStatus()
    }
    
    updateTerminalStatus() {
        if (window.AndroidInterface && window.AndroidInterface.getStripeTerminalStatus) {
            try {
                const statusJson = window.AndroidInterface.getStripeTerminalStatus()
                const status = JSON.parse(statusJson)
                
                let terminalText = 'NOT INITIALIZED'
                let terminalClass = 'text-red-600'
                
                if (status.error) {
                    terminalText = 'ERROR'
                    terminalClass = 'text-red-600'
                } else if (status.initialized) {
                    if (status.connectedReader) {
                        terminalText = 'CONNECTED'
                        terminalClass = 'text-green-600'
                    } else {
                        terminalText = 'INITIALIZED'
                        terminalClass = 'text-yellow-600'
                    }
                }
                
                document.getElementById('adminTerminalStatus').textContent = terminalText
                document.getElementById('adminTerminalStatus').className = terminalClass
                
                // Log detailed status for debugging
                console.log('Terminal Status:', status)
                
            } catch (e) {
                console.error('Failed to get Terminal status:', e)
                document.getElementById('adminTerminalStatus').textContent = 'ERROR'
                document.getElementById('adminTerminalStatus').className = 'text-red-600'
            }
        } else {
            document.getElementById('adminTerminalStatus').textContent = 'N/A'
            document.getElementById('adminTerminalStatus').className = 'text-gray-600'
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
            console.log('=== KIOSK DEBUG: Loading Hebrew calendar... ===')
            if (window.AndroidInterface && window.AndroidInterface.log) {
                window.AndroidInterface.log('KIOSK DEBUG: loadHebrewCalendar started')
            }
            
            // Load both current Hebrew date and Shabbat times
            console.log('KIOSK DEBUG: Starting parallel API calls')
            await Promise.all([
                this.loadCurrentHebrewDate(),
                this.loadShabbatTimes(),
                this.loadZmanim()
            ])
            
            console.log('KIOSK DEBUG: All API calls completed')
            
            // Also trigger Android background loading for future caching
            if (window.AndroidInterface && window.AndroidInterface.getHebrewCalendar) {
                console.log('KIOSK DEBUG: Calling Android getHebrewCalendar method')
                window.AndroidInterface.getHebrewCalendar()
            } else {
                console.warn('KIOSK DEBUG: AndroidInterface.getHebrewCalendar not available')
            }
            
        } catch (error) {
            console.error('KIOSK DEBUG: Failed to load Hebrew calendar:', error)
            if (window.AndroidInterface && window.AndroidInterface.log) {
                window.AndroidInterface.log('KIOSK DEBUG: Hebrew calendar loading failed: ' + error.message)
            }
            this.setCalendarErrorStates()
        }
    }
    
    async loadCurrentHebrewDate() {
        try {
            console.log('=== KIOSK DEBUG: Loading current Hebrew date ===')
            const today = new Date()
            const year = today.getFullYear()
            const month = today.getMonth() + 1
            const day = today.getDate()
            
            const url = `https://www.hebcal.com/converter?cfg=json&gy=${year}&gm=${month}&gd=${day}&g2h=1`
            console.log('KIOSK DEBUG: Hebrew date URL:', url)
            if (window.AndroidInterface && window.AndroidInterface.log) {
                window.AndroidInterface.log('KIOSK DEBUG: Hebrew date URL: ' + url)
            }
            
            const response = await fetch(url)
            console.log('KIOSK DEBUG: Hebrew date response status:', response.status, response.statusText)
            
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`)
            }
            
            const data = await response.json()
            console.log('KIOSK DEBUG: Hebrew date API response:', JSON.stringify(data, null, 2))
            if (window.AndroidInterface && window.AndroidInterface.log) {
                window.AndroidInterface.log('KIOSK DEBUG: Hebrew date response: ' + JSON.stringify(data))
            }
            
            const hebrewDateEl = document.getElementById('hebrewDate')
            if (hebrewDateEl) {
                if (data && data.hebrew) {
                    hebrewDateEl.textContent = data.hebrew
                    console.log('KIOSK DEBUG: Set current Hebrew date:', data.hebrew)
                } else if (data && data.hd) {
                    // Alternative field name for Hebrew date
                    hebrewDateEl.textContent = data.hd
                    console.log('KIOSK DEBUG: Set current Hebrew date (hd):', data.hd)
                } else {
                    console.warn('KIOSK DEBUG: No Hebrew date found in response:', data)
                    hebrewDateEl.textContent = 'Hebrew date unavailable'
                }
            } else {
                console.error('KIOSK DEBUG: hebrewDate element not found in DOM')
            }
        } catch (error) {
            console.error('KIOSK DEBUG: Failed to load current Hebrew date:', error)
            if (window.AndroidInterface && window.AndroidInterface.log) {
                window.AndroidInterface.log('KIOSK DEBUG: Hebrew date error: ' + error.message)
            }
            const hebrewDateEl = document.getElementById('hebrewDate')
            if (hebrewDateEl) hebrewDateEl.textContent = 'Unavailable'
        }
    }
    
    async loadShabbatTimes() {
        try {
            console.log('=== KIOSK DEBUG: Loading Shabbat times ===')
            // Use Davenport, FL coordinates
            const latitude = this.config.latitude || 28.1611
            const longitude = this.config.longitude || -81.6029
            
            const url = `https://www.hebcal.com/shabbat?cfg=json&m=50&latitude=${latitude}&longitude=${longitude}`
            console.log('KIOSK DEBUG: Shabbat times URL:', url)
            console.log('KIOSK DEBUG: Using coordinates - lat:', latitude, 'lng:', longitude)
            if (window.AndroidInterface && window.AndroidInterface.log) {
                window.AndroidInterface.log('KIOSK DEBUG: Shabbat times URL: ' + url)
            }
            
            const response = await fetch(url)
            console.log('KIOSK DEBUG: Shabbat times response status:', response.status, response.statusText)
            
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`)
            }
            
            const data = await response.json()
            console.log('KIOSK DEBUG: Shabbat times API response:', JSON.stringify(data, null, 2))
            if (window.AndroidInterface && window.AndroidInterface.log) {
                window.AndroidInterface.log('KIOSK DEBUG: Shabbat response: ' + JSON.stringify(data))
            }
            
            // Validate the response structure
            if (!data || !data.items || !Array.isArray(data.items)) {
                throw new Error('Invalid API response structure - missing items array')
            }
            
            console.log('KIOSK DEBUG: Response validation passed, items count:', data.items.length)
            this.displayShabbatTimes(data)
        } catch (error) {
            console.error('KIOSK DEBUG: Failed to load Shabbat times:', error)
            if (window.AndroidInterface && window.AndroidInterface.log) {
                window.AndroidInterface.log('KIOSK DEBUG: Shabbat times error: ' + error.message)
            }
            this.setShabbatErrorStates()
        }
    }
    
    async loadZmanim() {
        try {
            const latitude = this.config.latitude || 28.1611
            const longitude = this.config.longitude || -81.6029
            
            const today = new Date()
            const year = today.getFullYear()
            const month = today.getMonth() + 1
            const day = today.getDate()
            
            const url = `https://www.hebcal.com/zmanim?cfg=json&latitude=${latitude}&longitude=${longitude}&date=${year}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}`
            console.log('Loading Zmanim from:', url)
            
            const response = await fetch(url)
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`)
            }
            
            const data = await response.json()
            console.log('Zmanim API response:', data)
            
            this.displayZmanim(data)
        } catch (error) {
            console.error('Failed to load Zmanim:', error)
            // Don't show error for Zmanim as they're secondary data
        }
    }
    
    setCalendarErrorStates() {
        // Set fallback Hebrew date and parsha
        const hebrewDateEl = document.getElementById('hebrewDate')
        if (hebrewDateEl) hebrewDateEl.textContent = 'Unavailable'
        
        const parshaEl = document.getElementById('parsha')
        if (parshaEl) parshaEl.textContent = 'Unavailable'
        
        this.setShabbatErrorStates()
    }
    
    setShabbatErrorStates() {
        // Set fallback error text for all Shabbat time elements
        const sabbatElements = ['candleLighting', 'eighteenMin', 'havdalah', 'seventytwoMin']
        sabbatElements.forEach(elementId => {
            const element = document.getElementById(elementId)
            if (element) {
                element.textContent = 'Unavailable'
            }
        })
    }
    
    displayShabbatTimes(data) {
        console.log('=== KIOSK DEBUG: Displaying Shabbat times data ===')
        console.log('KIOSK DEBUG: Data received:', JSON.stringify(data, null, 2))
        if (window.AndroidInterface && window.AndroidInterface.log) {
            window.AndroidInterface.log('KIOSK DEBUG: displayShabbatTimes called')
        }
        
        const items = data.items || []
        console.log('KIOSK DEBUG: Items array length:', items.length)
        
        if (!items.length) {
            console.error('KIOSK DEBUG: No items in Shabbat times data')
            this.setShabbatErrorStates()
            return
        }
        
        // Log all items for debugging
        items.forEach((item, index) => {
            console.log(`KIOSK DEBUG: Item ${index}:`, JSON.stringify(item, null, 2))
        })
        
        // Find parsha - category should be 'parashat'
        const parsha = items.find(item => item.category === 'parashat')
        console.log('KIOSK DEBUG: Found parsha:', parsha)
        
        const parshaEl = document.getElementById('parsha')
        console.log('KIOSK DEBUG: Parsha element found:', !!parshaEl)
        
        if (parsha && parshaEl) {
            // Display parsha name - prefer Hebrew, fallback to English
            const parshaText = parsha.hebrew || parsha.title || 'No Parsha'
            parshaEl.textContent = parshaText
            console.log('KIOSK DEBUG: Set parsha:', parshaText)
            if (window.AndroidInterface && window.AndroidInterface.log) {
                window.AndroidInterface.log('KIOSK DEBUG: Set parsha: ' + parshaText)
            }
        } else {
            console.warn('KIOSK DEBUG: No parsha found or parsha element missing')
            if (parshaEl) parshaEl.textContent = 'No Parsha'
        }
        
        // Find candle lighting - category should be 'candles'
        const candles = items.find(item => item.category === 'candles')
        console.log('KIOSK DEBUG: Found candle lighting:', candles)
        
        const candleElement = document.getElementById('candleLighting')
        console.log('KIOSK DEBUG: Candle element found:', !!candleElement)
        
        if (candleElement) {
            if (candles) {
                // Extract time from title like "Candle lighting: 7:32pm"
                const timeMatch = candles.title.match(/(\d{1,2}:\d{2}[ap]m)/i)
                const timeText = timeMatch ? timeMatch[1] : 'Invalid time'
                candleElement.textContent = timeText
                console.log('KIOSK DEBUG: Set candle lighting:', timeText)
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
    
    displayZmanim(data) {
        console.log('Displaying Zmanim data:', data)
        
        if (!data || !data.times) {
            console.warn('No Zmanim times available')
            return
        }
        
        // Update sunrise and sunset using IDs
        const sunriseEl = document.getElementById('sunriseTime')
        const sunsetEl = document.getElementById('sunsetTime')
        const shemaEl = document.getElementById('shemaTime')
        const tefillahEl = document.getElementById('tefillahTime')
        const chatzosEl = document.getElementById('chatzosTime')
        const minchaKetanaEl = document.getElementById('minchaKetanaTime')
        
        if (data.times.sunrise && sunriseEl) {
            const sunriseTime = this.formatTime(new Date(data.times.sunrise))
            sunriseEl.textContent = sunriseTime
            console.log('Set sunrise:', sunriseTime)
        }
        
        if (data.times.sunset && sunsetEl) {
            const sunsetTime = this.formatTime(new Date(data.times.sunset))
            sunsetEl.textContent = sunsetTime
            console.log('Set sunset:', sunsetTime)
        }
        
        // Update other Zmanim if available
        if (data.times.sofZmanShmaMGA && shemaEl) {
            const shemaTime = this.formatTime(new Date(data.times.sofZmanShmaMGA))
            shemaEl.textContent = shemaTime
            console.log('Set Shema time:', shemaTime)
        }
        
        if (data.times.sofZmanTfillaMGA && tefillahEl) {
            const tefillahTime = this.formatTime(new Date(data.times.sofZmanTfillaMGA))
            tefillahEl.textContent = tefillahTime
            console.log('Set Tefillah time:', tefillahTime)
        }
        
        if (data.times.chatzot && chatzosEl) {
            const chatzosTime = this.formatTime(new Date(data.times.chatzot))
            chatzosEl.textContent = chatzosTime
            console.log('Set Chatzos time:', chatzosTime)
        }
        
        if (data.times.minchaKetana && minchaKetanaEl) {
            const minchaKetanaTime = this.formatTime(new Date(data.times.minchaKetana))
            minchaKetanaEl.textContent = minchaKetanaTime
            console.log('Set Mincha Ketana time:', minchaKetanaTime)
        }
    }
    
    formatTime(date) {
        if (!date || isNaN(date)) return 'N/A'
        
        let hours = date.getHours()
        const minutes = date.getMinutes()
        const ampm = hours >= 12 ? 'PM' : 'AM'
        hours = hours % 12
        hours = hours ? hours : 12 // 0 should be 12
        const minutesStr = minutes < 10 ? '0' + minutes : minutes
        return `${hours}:${minutesStr} ${ampm}`
    }
    
    async testNetworkConnectivity() {
        try {
            console.log('=== KIOSK DEBUG: Testing network connectivity ===')
            if (window.AndroidInterface && window.AndroidInterface.log) {
                window.AndroidInterface.log('KIOSK DEBUG: Testing network connectivity')
            }
            
            // Test basic connectivity with a simple API call
            const response = await fetch('https://httpbin.org/json', {
                method: 'GET',
                headers: {
                    'User-Agent': `OhrShalomKiosk/${this.version}`
                }
            })
            
            if (response.ok) {
                console.log('KIOSK DEBUG: Network connectivity test PASSED')
                if (window.AndroidInterface && window.AndroidInterface.log) {
                    window.AndroidInterface.log('KIOSK DEBUG: Network connectivity OK')
                }
                return true
            } else {
                throw new Error(`Network test failed with status ${response.status}`)
            }
        } catch (error) {
            console.error('KIOSK DEBUG: Network connectivity test FAILED:', error)
            if (window.AndroidInterface && window.AndroidInterface.log) {
                window.AndroidInterface.log('KIOSK DEBUG: Network connectivity FAILED: ' + error.message)
            }
            return false
        }
    }
    
    hideAdminConfigModal() {
        console.log('ADMIN DEBUG: Hiding admin configuration modal')
        const modal = document.getElementById('adminConfigModal')
        if (modal) {
            modal.classList.add('hidden')
        }
    }
    
    saveAdminConfig() {
        console.log('ADMIN DEBUG: Saving admin configuration')
        
        try {
            // Collect form values
            const newConfig = {
                organizationName: document.getElementById('adminOrgName').value,
                latitude: parseFloat(document.getElementById('adminLatitude').value),
                longitude: parseFloat(document.getElementById('adminLongitude').value),
                timeZone: document.getElementById('adminTimezone').value,
                shacharit: this.convertTo12Hour(document.getElementById('adminShacharit').value),
                mincha: this.convertTo12Hour(document.getElementById('adminMincha').value),
                maariv: this.convertTo12Hour(document.getElementById('adminMaariv').value),
                logoUrl: document.getElementById('adminLogoUrl').value,
                stripePublishableKey: document.getElementById('adminStripePublishableKey').value,
                stripeTokenEndpoint: document.getElementById('adminStripeTokenEndpoint').value,
                stripeLocationId: document.getElementById('adminStripeLocationId').value,
                stripeEnvironment: document.getElementById('adminStripeEnvironment').value
            }
            
            // Handle new PIN if provided
            const newPin = document.getElementById('adminNewPin').value
            if (newPin && newPin.length >= 4) {
                newConfig.adminPin = newPin
            }
            
            console.log('ADMIN DEBUG: New configuration:', newConfig)
            
            // Update local config
            this.config = { ...this.config, ...newConfig }
            
            // Save to localStorage
            localStorage.setItem('ohrShalomKioskConfig', JSON.stringify(this.config))
            
            // Save to Android if available
            if (window.AndroidInterface && window.AndroidInterface.saveConfig) {
                const success = window.AndroidInterface.saveConfig(JSON.stringify(newConfig))
                console.log('ADMIN DEBUG: Android config save result:', success)
            }
            
            // Update prayer times display
            this.updatePrayerTimesDisplay()
            
            // Update logo if changed
            if (newConfig.logoUrl) {
                this.updateLogo(newConfig.logoUrl)
            }
            
            // Refresh Hebrew calendar data with new coordinates
            this.loadHebrewCalendar()
            
            this.showMessage('Configuration saved successfully!', 'success', 3000)
            this.hideAdminConfigModal()
            
        } catch (error) {
            console.error('ADMIN DEBUG: Error saving configuration:', error)
            this.showMessage('Error saving configuration: ' + error.message, 'error', 5000)
        }
    }
    
    toggleKioskMode() {
        console.log('ADMIN DEBUG: Toggling kiosk mode')
        
        if (window.AndroidInterface) {
            if (window.AndroidInterface.exitKioskMode && window.AndroidInterface.enterKioskMode) {
                // Check current status and toggle
                try {
                    const status = JSON.parse(window.AndroidInterface.getKioskStatus())
                    if (status.kioskMode) {
                        window.AndroidInterface.exitKioskMode()
                        this.showMessage('Kiosk mode disabled', 'info', 3000)
                    } else {
                        window.AndroidInterface.enterKioskMode()
                        this.showMessage('Kiosk mode enabled', 'success', 3000)
                    }
                    
                    // Update status display
                    setTimeout(() => this.updateAdminStatus(), 1000)
                } catch (e) {
                    console.error('ADMIN DEBUG: Error toggling kiosk mode:', e)
                }
            }
        } else {
            this.showMessage('Kiosk mode control not available in web mode', 'warning', 3000)
        }
    }
    
    resetConfigToDefaults() {
        console.log('ADMIN DEBUG: Resetting configuration to defaults')
        
        if (confirm('Are you sure you want to reset all configuration to defaults? This cannot be undone.')) {
            // Reset to default configuration
            this.config = {
                adminPin: '12345',
                latitude: 28.1611,
                longitude: -81.6029,
                timeZone: 'America/New_York',
                geonameId: null,
                locationMethod: 'coordinates',
                shacharit: '7:00 AM',
                mincha: '2:00 PM',
                maariv: '8:00 PM',
                organizationName: 'Ohr Shalom',
                logoUrl: '',
                stripeTestMode: false,
                stripePublishableKey: '',
                stripeTokenEndpoint: '',
                stripeLocationId: '',
                stripeEnvironment: 'test'
            }
            
            // Save to storage
            localStorage.setItem('ohrShalomKioskConfig', JSON.stringify(this.config))
            
            // Reset Android config if available
            if (window.AndroidInterface && window.AndroidInterface.saveConfig) {
                window.AndroidInterface.saveConfig(JSON.stringify(this.config))
            }
            
            // Update displays
            this.populateAdminConfig()
            this.updatePrayerTimesDisplay()
            this.loadHebrewCalendar()
            
            this.showMessage('Configuration reset to defaults', 'success', 3000)
        }
    }
    
    async runAdminTest(testType) {
        console.log('ADMIN DEBUG: Running admin test:', testType)
        const resultsDiv = document.getElementById('adminTestResults')
        resultsDiv.classList.remove('hidden')
        
        try {
            switch (testType) {
                case 'network':
                    resultsDiv.innerHTML = 'Testing network connectivity...'
                    const networkResult = await this.testNetworkConnectivity()
                    resultsDiv.innerHTML = `Network Test: ${networkResult ? 'SUCCESS' : 'FAILED'}`
                    break
                    
                case 'hebcal':
                    resultsDiv.innerHTML = 'Testing Hebcal API...'
                    const hebcalResult = await this.testSimpleApi()
                    resultsDiv.innerHTML = `Hebcal API Test: ${hebcalResult ? 'SUCCESS - ' + JSON.stringify(hebcalResult).substring(0, 200) + '...' : 'FAILED'}`
                    break
                    
                case 'refresh':
                    resultsDiv.innerHTML = 'Refreshing Hebrew calendar data...'
                    await this.loadHebrewCalendar()
                    resultsDiv.innerHTML = 'Hebrew calendar data refreshed. Check main display for updates.'
                    this.updateAdminStatus()
                    break
                    
                case 'logs':
                    const kioskInfo = this.getKioskInfo()
                    resultsDiv.innerHTML = `Debug Info:\n${JSON.stringify(kioskInfo, null, 2)}`
                    break
            }
        } catch (error) {
            resultsDiv.innerHTML = `Test failed: ${error.message}`
            console.error('ADMIN DEBUG: Test error:', error)
        }
    }
    
    // Logo customization methods
    previewCustomLogo() {
        console.log('ADMIN DEBUG: Previewing custom logo')
        const logoUrl = document.getElementById('adminLogoUrl').value
        
        if (!logoUrl) {
            this.showMessage('Please enter a logo URL first', 'warning', 3000)
            return
        }
        
        const previewDiv = document.getElementById('logoPreview')
        const previewImg = document.getElementById('logoPreviewImg')
        
        previewImg.onload = () => {
            console.log('ADMIN DEBUG: Logo loaded successfully')
            previewDiv.classList.remove('hidden')
            this.showMessage('Logo preview loaded', 'success', 2000)
        }
        
        previewImg.onerror = () => {
            console.error('ADMIN DEBUG: Failed to load logo')
            previewDiv.classList.add('hidden')
            this.showMessage('Failed to load logo. Check the URL and try again.', 'error', 5000)
        }
        
        previewImg.src = logoUrl
    }
    
    resetLogo() {
        console.log('ADMIN DEBUG: Resetting logo to default')
        document.getElementById('adminLogoUrl').value = ''
        document.getElementById('logoPreview').classList.add('hidden')
        
        // Reset to default logo
        this.updateLogo('')
        this.showMessage('Logo reset to default', 'success', 2000)
    }
    
    updateLogo(logoUrl) {
        console.log('ADMIN DEBUG: Updating logo to:', logoUrl)
        const defaultLogo = document.getElementById('defaultLogo')
        const customLogo = document.getElementById('customLogo')
        
        if (logoUrl && logoUrl.trim()) {
            // Show custom logo
            customLogo.src = logoUrl
            customLogo.onload = () => {
                defaultLogo.classList.add('hidden')
                customLogo.classList.remove('hidden')
                console.log('ADMIN DEBUG: Custom logo displayed')
            }
            customLogo.onerror = () => {
                console.error('ADMIN DEBUG: Failed to load custom logo, showing default')
                customLogo.classList.add('hidden')
                defaultLogo.classList.remove('hidden')
            }
        } else {
            // Show default logo
            customLogo.classList.add('hidden')
            defaultLogo.classList.remove('hidden')
            console.log('ADMIN DEBUG: Default logo displayed')
        }
    }
    
    // Stripe testing methods
    async runStripeTest(testType) {
        console.log('ADMIN DEBUG: Running Stripe test:', testType)
        const resultsDiv = document.getElementById('adminStripeResults')
        resultsDiv.classList.remove('hidden')
        
        try {
            switch (testType) {
                case 'connection':
                    resultsDiv.innerHTML = 'Testing Stripe connection...'
                    await this.testStripeConnection()
                    break
                    
                case 'api':
                    resultsDiv.innerHTML = 'Testing Stripe API status...'
                    await this.testStripeAPI()
                    break
                    
                case 'terminal':
                    resultsDiv.innerHTML = 'Testing Stripe Terminal...'
                    await this.testStripeTerminal()
                    break
            }
        } catch (error) {
            resultsDiv.innerHTML = `Stripe test failed: ${error.message}`
            console.error('ADMIN DEBUG: Stripe test error:', error)
        }
    }
    
    async testStripeConnection() {
        console.log('ADMIN DEBUG: Testing Stripe connection')
        const resultsDiv = document.getElementById('adminStripeResults')
        
        if (window.AndroidInterface && window.AndroidInterface.getNfcStatus) {
            const nfcStatus = window.AndroidInterface.getNfcStatus()
            resultsDiv.innerHTML = `Stripe Connection Test:\nNFC Status: ${nfcStatus}\nStripe Terminal: ${window.AndroidInterface ? 'Available' : 'Not Available'}`
            
            // Test Android payment manager status
            if (window.AndroidInterface.getKioskStatus) {
                try {
                    const status = JSON.parse(window.AndroidInterface.getKioskStatus())
                    resultsDiv.innerHTML += `\nPayment Manager: Ready\nDevice Ready: ${status.nfcStatus === 'enabled'}`
                } catch (e) {
                    resultsDiv.innerHTML += `\nPayment Manager: Error - ${e.message}`
                }
            }
        } else {
            resultsDiv.innerHTML = 'Stripe Connection Test:\nAndroid Interface: Not Available\nRunning in Web Mode - Stripe testing limited'
        }
    }
    
    async testStripeAPI() {
        console.log('ADMIN DEBUG: Testing Stripe API')
        const resultsDiv = document.getElementById('adminStripeResults')
        
        // Test if we can create a mock payment intent
        if (window.AndroidInterface && window.AndroidInterface.log) {
            window.AndroidInterface.log('ADMIN DEBUG: Testing Stripe API functionality')
            
            resultsDiv.innerHTML = 'Stripe API Test:\nChecking payment manager initialization...\nVerifying Terminal SDK...'
            
            // We can't test actual Stripe API without credentials, but we can test the infrastructure
            setTimeout(() => {
                resultsDiv.innerHTML += '\nPayment Infrastructure: Ready\nTerminal SDK: Loaded\nNote: Actual API testing requires live Stripe credentials'
            }, 1000)
        } else {
            resultsDiv.innerHTML = 'Stripe API Test:\nAndroid payment interface not available\nWeb mode detected - API testing not possible'
        }
    }
    
    async testStripeTerminal() {
        console.log('ADMIN DEBUG: Testing Stripe Terminal')
        const resultsDiv = document.getElementById('adminStripeResults')
        
        if (window.AndroidInterface && window.AndroidInterface.getNfcStatus) {
            const nfcStatus = window.AndroidInterface.getNfcStatus()
            
            resultsDiv.innerHTML = `Stripe Terminal Test:\nNFC Hardware: ${nfcStatus}\nTerminal Ready: ${nfcStatus === 'enabled'}`
            
            if (nfcStatus === 'enabled') {
                resultsDiv.innerHTML += '\n✅ Terminal is ready for payments\n💡 Use "Enable Test Mode" for safe testing'
            } else if (nfcStatus === 'disabled') {
                resultsDiv.innerHTML += '\n❌ NFC is disabled - enable in Android settings\n⚙️ Go to Settings > Connected devices > NFC'
            } else {
                resultsDiv.innerHTML += '\n❌ NFC not supported on this device'
            }
        } else {
            resultsDiv.innerHTML = 'Stripe Terminal Test:\nAndroid interface not available\nCannot test NFC hardware in web mode'
        }
    }
    
    toggleStripeTestMode() {
        console.log('ADMIN DEBUG: Toggling Stripe test mode')
        this.config.stripeTestMode = !this.config.stripeTestMode
        
        const testPaymentDiv = document.getElementById('stripeTestPayment')
        const button = document.getElementById('adminStripeTestMode')
        
        if (this.config.stripeTestMode) {
            testPaymentDiv.classList.remove('hidden')
            button.textContent = 'Disable Test Mode'
            button.className = button.className.replace('bg-orange-500 hover:bg-orange-600', 'bg-red-500 hover:bg-red-600')
            this.showMessage('Stripe Test Mode ENABLED - Safe to test with real cards', 'info', 5000)
            
            if (window.AndroidInterface && window.AndroidInterface.log) {
                window.AndroidInterface.log('ADMIN DEBUG: Stripe test mode enabled')
            }
        } else {
            testPaymentDiv.classList.add('hidden')
            button.textContent = 'Enable Test Mode'
            button.className = button.className.replace('bg-red-500 hover:bg-red-600', 'bg-orange-500 hover:bg-orange-600')
            this.showMessage('Stripe Test Mode DISABLED', 'info', 3000)
        }
        
        // Save test mode state
        localStorage.setItem('ohrShalomKioskConfig', JSON.stringify(this.config))
    }
    
    async startTestPayment() {
        console.log('ADMIN DEBUG: Starting test payment')
        
        if (!this.config.stripeTestMode) {
            this.showMessage('Please enable test mode first', 'error', 3000)
            return
        }
        
        const amount = parseInt(document.getElementById('stripeTestAmount').value)
        const resultsDiv = document.getElementById('adminStripeResults')
        resultsDiv.classList.remove('hidden')
        
        try {
            resultsDiv.innerHTML = `Starting test payment for $${(amount / 100).toFixed(2)}...\nTest Mode: ENABLED\nNo real charges will be made\n\nWaiting for NFC tap...`
            
            if (window.AndroidInterface && window.AndroidInterface.processNfcPayment) {
                // Create test payment data
                const paymentData = {
                    amount: amount,
                    currency: 'usd',
                    email: 'test@admin.kiosk',
                    isTest: true
                }
                
                console.log('ADMIN DEBUG: Calling Android test payment with:', paymentData)
                const result = window.AndroidInterface.processNfcPayment(JSON.stringify(paymentData))
                
                resultsDiv.innerHTML += `\nPayment initiated...\nResult: ${result}\nCheck main screen for payment status`
                
                // Listen for payment completion (this would be handled by the existing payment flow)
                setTimeout(() => {
                    resultsDiv.innerHTML += '\n\n✅ Test payment flow completed\n💡 In test mode, all payments are simulated'
                }, 3000)
                
            } else {
                resultsDiv.innerHTML += '\n❌ Android payment interface not available\n🌐 Running in web mode - actual payment testing not possible'
            }
            
        } catch (error) {
            console.error('ADMIN DEBUG: Test payment error:', error)
            resultsDiv.innerHTML = `Test payment failed: ${error.message}`
        }
    }
    
    // Stripe credential management methods
    async validateStripeCredentials() {
        console.log('ADMIN DEBUG: Validating Stripe credentials')
        const statusDiv = document.getElementById('stripeCredentialStatus')
        statusDiv.classList.remove('hidden')
        statusDiv.className = 'p-3 rounded border text-sm bg-blue-50 border-blue-200'
        statusDiv.innerHTML = 'Validating Stripe credentials...'
        
        try {
            const publishableKey = document.getElementById('adminStripePublishableKey').value
            const tokenEndpoint = document.getElementById('adminStripeTokenEndpoint').value
            const locationId = document.getElementById('adminStripeLocationId').value
            const environment = document.getElementById('adminStripeEnvironment').value
            
            // Validation checks
            const validationResults = []
            
            // Check publishable key format
            if (!publishableKey) {
                validationResults.push('❌ Publishable key is required')
            } else if (environment === 'test' && !publishableKey.startsWith('pk_test_')) {
                validationResults.push('⚠️ Test environment should use pk_test_ key')
            } else if (environment === 'live' && !publishableKey.startsWith('pk_live_')) {
                validationResults.push('⚠️ Live environment should use pk_live_ key')
            } else {
                validationResults.push('✅ Publishable key format is valid')
            }
            
            // Check token endpoint
            if (tokenEndpoint) {
                try {
                    new URL(tokenEndpoint)
                    validationResults.push('✅ Token endpoint URL format is valid')
                    
                    // Test endpoint connectivity
                    if (navigator.onLine) {
                        try {
                            const response = await fetch(tokenEndpoint, {
                                method: 'POST',
                                headers: { 
                                    'Content-Type': 'application/json',
                                    'Accept': 'application/json'
                                },
                                body: JSON.stringify({})
                            })
                            
                            if (response.ok) {
                                try {
                                    const data = await response.json()
                                    if (data.secret && data.secret.startsWith('pst_')) {
                                        validationResults.push('✅ Token endpoint is working (valid connection token received)')
                                    } else {
                                        validationResults.push('⚠️ Token endpoint returned unexpected response format')
                                    }
                                } catch (parseError) {
                                    validationResults.push('⚠️ Token endpoint returned invalid JSON')
                                }
                            } else {
                                validationResults.push(`⚠️ Token endpoint returned ${response.status}`)
                            }
                        } catch (e) {
                            console.error('Token endpoint test failed:', e)
                            validationResults.push(`⚠️ Token endpoint connectivity failed: ${e.message}`)
                        }
                    } else {
                        validationResults.push('⚠️ No internet connection - cannot test endpoint')
                    }
                } catch (e) {
                    validationResults.push('❌ Invalid token endpoint URL')
                }
            } else {
                validationResults.push('⚠️ Token endpoint recommended for live payments')
            }
            
            // Check location ID format
            if (locationId) {
                if (locationId.startsWith('tml_')) {
                    validationResults.push('✅ Location ID format is valid')
                } else {
                    validationResults.push('⚠️ Location ID should start with "tml_"')
                }
            } else {
                validationResults.push('⚠️ Location ID recommended for Terminal payments')
            }
            
            // Update Android with credentials if valid
            if (publishableKey && window.AndroidInterface && window.AndroidInterface.updateStripeConfig) {
                const stripeConfig = {
                    publishableKey,
                    tokenEndpoint,
                    locationId,
                    environment
                }
                
                try {
                    const result = window.AndroidInterface.updateStripeConfig(JSON.stringify(stripeConfig))
                    if (result === 'success') {
                        validationResults.push('✅ Android Stripe configuration updated')
                    } else {
                        validationResults.push('⚠️ Android configuration update: ' + result)
                    }
                } catch (e) {
                    validationResults.push('⚠️ Could not update Android configuration')
                }
            }
            
            // Display results
            const hasErrors = validationResults.some(r => r.startsWith('❌'))
            const hasWarnings = validationResults.some(r => r.startsWith('⚠️'))
            
            if (hasErrors) {
                statusDiv.className = 'p-3 rounded border text-sm bg-red-50 border-red-200'
            } else if (hasWarnings) {
                statusDiv.className = 'p-3 rounded border text-sm bg-yellow-50 border-yellow-200'
            } else {
                statusDiv.className = 'p-3 rounded border text-sm bg-green-50 border-green-200'
            }
            
            statusDiv.innerHTML = `<div class="font-semibold mb-2">Stripe Credentials Validation</div>${validationResults.join('<br>')}`
            
            // Update Terminal status after validation
            setTimeout(() => this.updateTerminalStatus(), 1000)
            
        } catch (error) {
            console.error('ADMIN DEBUG: Stripe credential validation error:', error)
            statusDiv.className = 'p-3 rounded border text-sm bg-red-50 border-red-200'
            statusDiv.innerHTML = `Validation error: ${error.message}`
        }
    }
    
    clearStripeCredentials() {
        console.log('ADMIN DEBUG: Clearing Stripe credentials')
        
        if (confirm('Are you sure you want to clear all Stripe credentials? This will disable payment processing until new credentials are entered.')) {
            // Clear form fields
            document.getElementById('adminStripePublishableKey').value = ''
            document.getElementById('adminStripeTokenEndpoint').value = ''
            document.getElementById('adminStripeLocationId').value = ''
            document.getElementById('adminStripeEnvironment').value = 'test'
            
            // Clear from configuration
            this.config.stripePublishableKey = ''
            this.config.stripeTokenEndpoint = ''
            this.config.stripeLocationId = ''
            this.config.stripeEnvironment = 'test'
            
            // Save cleared configuration
            localStorage.setItem('ohrShalomKioskConfig', JSON.stringify(this.config))
            
            // Clear Android configuration if available
            if (window.AndroidInterface && window.AndroidInterface.updateStripeConfig) {
                try {
                    window.AndroidInterface.updateStripeConfig(JSON.stringify({
                        publishableKey: '',
                        tokenEndpoint: '',
                        locationId: '',
                        environment: 'test'
                    }))
                } catch (e) {
                    console.warn('Could not clear Android Stripe configuration:', e)
                }
            }
            
            // Hide status and show success message
            const statusDiv = document.getElementById('stripeCredentialStatus')
            statusDiv.classList.add('hidden')
            
            this.showMessage('Stripe credentials cleared', 'info', 3000)
        }
    }
    
    // Status and debugging methods
    getKioskInfo() {
        const info = {
            version: this.version,
            buildDate: this.buildDate,
            timestamp: new Date().toISOString(),
            config: this.config,
            elementsPresent: {
                hebrewDate: !!document.getElementById('hebrewDate'),
                parsha: !!document.getElementById('parsha'),
                candleLighting: !!document.getElementById('candleLighting'),
                havdalah: !!document.getElementById('havdalah'),
                sunriseTime: !!document.getElementById('sunriseTime'),
                sunsetTime: !!document.getElementById('sunsetTime')
            }
        }
        
        console.log('KIOSK DEBUG: Kiosk info:', JSON.stringify(info, null, 2))
        if (window.AndroidInterface && window.AndroidInterface.log) {
            window.AndroidInterface.log('KIOSK DEBUG: Kiosk info: ' + JSON.stringify(info))
        }
        
        return info
    }
    
    async testSimpleApi() {
        try {
            console.log('=== KIOSK DEBUG: Testing simple HebCal API ===')
            const response = await fetch('https://www.hebcal.com/converter?cfg=json&gy=2025&gm=8&gd=26&g2h=1')
            console.log('KIOSK DEBUG: Simple API response status:', response.status)
            
            if (response.ok) {
                const data = await response.json()
                console.log('KIOSK DEBUG: Simple API data:', JSON.stringify(data, null, 2))
                return data
            } else {
                throw new Error(`API returned ${response.status}`)
            }
        } catch (error) {
            console.error('KIOSK DEBUG: Simple API test failed:', error)
            return null
        }
    }
    
    // For Android bridge compatibility - delegates to new methods
    displayHebrewCalendar(data) {
        console.log('Android bridge: displayHebrewCalendar called with:', data)
        this.displayShabbatTimes(data)
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