module.exports = {
  apps: [{
    name: 'ohr-shalom-backend',
    script: 'server.js',
    
    // Environment settings
    env: {
      NODE_ENV: 'development',
      PORT: 3000
    },
    env_production: {
      NODE_ENV: 'production',
      PORT: 3000
    },
    
    // Process management
    instances: 1, // Single instance for simplicity
    exec_mode: 'fork',
    
    // Restart policy
    max_restarts: 10,
    min_uptime: '10s',
    max_memory_restart: '200M',
    
    // Logging
    log_file: './logs/combined.log',
    out_file: './logs/out.log',
    error_file: './logs/error.log',
    log_date_format: 'YYYY-MM-DD HH:mm:ss Z',
    
    // Monitoring
    monitoring: false,
    
    // Auto-restart on file changes (development only)
    watch: process.env.NODE_ENV !== 'production' ? ['server.js', 'routes/', 'middleware/'] : false,
    ignore_watch: ['node_modules', 'logs', '.git'],
    
    // Process management
    kill_timeout: 5000,
    listen_timeout: 3000,
    shutdown_with_message: true,
    
    // Node.js specific
    node_args: ['--max-old-space-size=200'],
    
    // Environment variables
    env_file: '.env'
  }]
};