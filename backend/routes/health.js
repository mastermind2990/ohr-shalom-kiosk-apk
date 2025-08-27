const express = require('express');
const router = express.Router();

/**
 * GET /health
 * 
 * Health check endpoint for monitoring and load balancers
 */
router.get('/', (req, res) => {
  const uptime = process.uptime();
  const memoryUsage = process.memoryUsage();
  
  const health = {
    status: 'healthy',
    timestamp: new Date().toISOString(),
    uptime: `${Math.floor(uptime / 3600)}h ${Math.floor((uptime % 3600) / 60)}m ${Math.floor(uptime % 60)}s`,
    uptimeSeconds: uptime,
    environment: process.env.NODE_ENV || 'development',
    version: '1.0.0',
    memory: {
      rss: `${Math.round(memoryUsage.rss / 1024 / 1024 * 100) / 100} MB`,
      heapTotal: `${Math.round(memoryUsage.heapTotal / 1024 / 1024 * 100) / 100} MB`,
      heapUsed: `${Math.round(memoryUsage.heapUsed / 1024 / 1024 * 100) / 100} MB`,
      external: `${Math.round(memoryUsage.external / 1024 / 1024 * 100) / 100} MB`
    },
    stripe: {
      configured: !!process.env.STRIPE_SECRET_KEY,
      mode: process.env.STRIPE_SECRET_KEY ? 
        (process.env.STRIPE_SECRET_KEY.startsWith('sk_live_') ? 'live' : 'test') : 
        'not_configured'
    }
  };
  
  res.json(health);
});

/**
 * GET /health/ready
 * 
 * Readiness probe for Kubernetes/container orchestration
 */
router.get('/ready', (req, res) => {
  // Check if all critical services are ready
  const isReady = process.env.STRIPE_SECRET_KEY ? true : false;
  
  if (isReady) {
    res.json({
      status: 'ready',
      timestamp: new Date().toISOString(),
      services: {
        stripe: !!process.env.STRIPE_SECRET_KEY
      }
    });
  } else {
    res.status(503).json({
      status: 'not_ready',
      timestamp: new Date().toISOString(),
      message: 'Stripe configuration missing',
      services: {
        stripe: false
      }
    });
  }
});

/**
 * GET /health/live
 * 
 * Liveness probe for Kubernetes/container orchestration
 */
router.get('/live', (req, res) => {
  res.json({
    status: 'alive',
    timestamp: new Date().toISOString(),
    pid: process.pid
  });
});

module.exports = router;