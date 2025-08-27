const express = require('express');
const { body, validationResult } = require('express-validator');
const Stripe = require('stripe');

const router = express.Router();

// Initialize Stripe - will be configured when server starts
let stripe = null;

// Initialize Stripe with secret key
function initializeStripe() {
  if (!process.env.STRIPE_SECRET_KEY) {
    console.error('❌ STRIPE_SECRET_KEY not found in environment variables');
    return false;
  }
  
  try {
    stripe = new Stripe(process.env.STRIPE_SECRET_KEY);
    const isLive = process.env.STRIPE_SECRET_KEY.startsWith('sk_live_');
    console.log(`✅ Stripe initialized in ${isLive ? 'LIVE' : 'TEST'} mode`);
    return true;
  } catch (error) {
    console.error('❌ Failed to initialize Stripe:', error.message);
    return false;
  }
}

// Middleware to ensure Stripe is initialized
const ensureStripe = (req, res, next) => {
  if (!stripe) {
    if (!initializeStripe()) {
      return res.status(500).json({
        error: 'Stripe not configured',
        message: 'STRIPE_SECRET_KEY environment variable is required',
        timestamp: new Date().toISOString()
      });
    }
  }
  next();
};

/**
 * POST /api/stripe/connection_token
 * 
 * Generate a connection token for Stripe Terminal
 * This endpoint is called by the Android app to authenticate with Stripe Terminal
 */
router.post('/connection_token', ensureStripe, async (req, res) => {
  try {
    console.log('🔗 Generating Stripe Terminal connection token...');
    
    // Create connection token
    const connectionToken = await stripe.terminal.connectionTokens.create();
    
    console.log('✅ Connection token generated successfully');
    
    res.json({
      secret: connectionToken.secret,
      created: connectionToken.created,
      livemode: connectionToken.livemode
    });
    
  } catch (error) {
    console.error('❌ Failed to create connection token:', error);
    
    res.status(500).json({
      error: 'Failed to create connection token',
      message: error.message,
      type: error.type || 'api_error',
      timestamp: new Date().toISOString()
    });
  }
});

/**
 * POST /api/stripe/payment_intent
 * 
 * Create a payment intent for Terminal payments
 */
router.post('/payment_intent', [
  ensureStripe,
  body('amount').isInt({ min: 1 }).withMessage('Amount must be a positive integer in cents'),
  body('currency').isAlpha().isLength({ min: 3, max: 3 }).withMessage('Currency must be a 3-letter code'),
  body('email').optional().isEmail().withMessage('Email must be valid'),
  body('description').optional().isString().isLength({ max: 500 }).withMessage('Description too long')
], async (req, res) => {
  try {
    // Check validation results
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({
        error: 'Validation error',
        details: errors.array(),
        timestamp: new Date().toISOString()
      });
    }
    
    const { amount, currency = 'usd', email, description = 'Ohr Shalom Donation' } = req.body;
    
    console.log(`💳 Creating payment intent: $${(amount / 100).toFixed(2)} ${currency.toUpperCase()}`);
    
    // Create payment intent parameters
    const paymentIntentParams = {
      amount: parseInt(amount),
      currency: currency.toLowerCase(),
      payment_method_types: ['card_present'],
      capture_method: 'automatic',
      description,
      metadata: {
        source: 'ohr_shalom_kiosk',
        timestamp: new Date().toISOString()
      }
    };
    
    // Add receipt email if provided
    if (email) {
      paymentIntentParams.receipt_email = email;
      paymentIntentParams.metadata.email = email;
    }
    
    // Create the payment intent
    const paymentIntent = await stripe.paymentIntents.create(paymentIntentParams);
    
    console.log(`✅ Payment intent created: ${paymentIntent.id}`);
    
    res.json({
      id: paymentIntent.id,
      client_secret: paymentIntent.client_secret,
      amount: paymentIntent.amount,
      currency: paymentIntent.currency,
      status: paymentIntent.status,
      created: paymentIntent.created,
      description: paymentIntent.description
    });
    
  } catch (error) {
    console.error('❌ Failed to create payment intent:', error);
    
    res.status(500).json({
      error: 'Failed to create payment intent',
      message: error.message,
      type: error.type || 'api_error',
      timestamp: new Date().toISOString()
    });
  }
});

/**
 * GET /api/stripe/locations
 * 
 * List available Terminal locations
 */
router.get('/locations', ensureStripe, async (req, res) => {
  try {
    console.log('📍 Fetching Stripe Terminal locations...');
    
    const locations = await stripe.terminal.locations.list({
      limit: 100
    });
    
    console.log(`✅ Found ${locations.data.length} Terminal locations`);
    
    res.json({
      locations: locations.data.map(location => ({
        id: location.id,
        display_name: location.display_name,
        address: location.address,
        livemode: location.livemode,
        metadata: location.metadata
      })),
      has_more: locations.has_more,
      count: locations.data.length
    });
    
  } catch (error) {
    console.error('❌ Failed to fetch locations:', error);
    
    res.status(500).json({
      error: 'Failed to fetch locations',
      message: error.message,
      type: error.type || 'api_error',
      timestamp: new Date().toISOString()
    });
  }
});

/**
 * POST /api/stripe/locations
 * 
 * Create a new Terminal location
 */
router.post('/locations', [
  ensureStripe,
  body('display_name').isString().isLength({ min: 1, max: 100 }).withMessage('Display name required'),
  body('address.line1').isString().isLength({ min: 1 }).withMessage('Address line 1 required'),
  body('address.city').isString().isLength({ min: 1 }).withMessage('City required'),
  body('address.state').isString().isLength({ min: 2, max: 2 }).withMessage('State must be 2 characters'),
  body('address.postal_code').isString().isLength({ min: 5 }).withMessage('Postal code required'),
  body('address.country').isString().isLength({ min: 2, max: 2 }).withMessage('Country must be 2 characters')
], async (req, res) => {
  try {
    // Check validation results
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({
        error: 'Validation error',
        details: errors.array(),
        timestamp: new Date().toISOString()
      });
    }
    
    const { display_name, address } = req.body;
    
    console.log(`📍 Creating Terminal location: ${display_name}`);
    
    const location = await stripe.terminal.locations.create({
      display_name,
      address: {
        line1: address.line1,
        line2: address.line2 || undefined,
        city: address.city,
        state: address.state,
        postal_code: address.postal_code,
        country: address.country
      },
      metadata: {
        source: 'ohr_shalom_kiosk',
        created_by: 'kiosk_backend',
        timestamp: new Date().toISOString()
      }
    });
    
    console.log(`✅ Terminal location created: ${location.id}`);
    
    res.status(201).json({
      id: location.id,
      display_name: location.display_name,
      address: location.address,
      livemode: location.livemode,
      created: location.created
    });
    
  } catch (error) {
    console.error('❌ Failed to create location:', error);
    
    res.status(500).json({
      error: 'Failed to create location',
      message: error.message,
      type: error.type || 'api_error',
      timestamp: new Date().toISOString()
    });
  }
});

/**
 * GET /api/stripe/config
 * 
 * Get current Stripe configuration info (safe details only)
 */
router.get('/config', ensureStripe, async (req, res) => {
  try {
    const isLive = process.env.STRIPE_SECRET_KEY.startsWith('sk_live_');
    
    res.json({
      mode: isLive ? 'live' : 'test',
      version: require('stripe/package.json').version,
      initialized: !!stripe,
      timestamp: new Date().toISOString()
    });
    
  } catch (error) {
    console.error('❌ Failed to get config:', error);
    
    res.status(500).json({
      error: 'Failed to get configuration',
      message: error.message,
      timestamp: new Date().toISOString()
    });
  }
});

// Initialize Stripe when module loads
initializeStripe();

module.exports = router;