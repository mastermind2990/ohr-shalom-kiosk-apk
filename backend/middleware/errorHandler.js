/**
 * Global error handler middleware
 * 
 * Handles all unhandled errors and provides consistent error responses
 */
function errorHandler(err, req, res, next) {
  // Log error details
  console.error('🚨 Unhandled Error:', {
    message: err.message,
    stack: err.stack,
    url: req.originalUrl,
    method: req.method,
    ip: req.ip,
    userAgent: req.get('User-Agent'),
    timestamp: new Date().toISOString()
  });
  
  // Default error response
  let status = 500;
  let message = 'Internal Server Error';
  let type = 'server_error';
  
  // Handle specific error types
  if (err.name === 'ValidationError') {
    status = 400;
    message = 'Validation Error';
    type = 'validation_error';
  } else if (err.name === 'CastError') {
    status = 400;
    message = 'Invalid ID format';
    type = 'cast_error';
  } else if (err.code === 11000) {
    status = 409;
    message = 'Resource already exists';
    type = 'duplicate_error';
  } else if (err.name === 'JsonWebTokenError') {
    status = 401;
    message = 'Invalid token';
    type = 'auth_error';
  } else if (err.name === 'TokenExpiredError') {
    status = 401;
    message = 'Token expired';
    type = 'auth_error';
  } else if (err.status) {
    status = err.status;
    message = err.message;
  }
  
  // Don't expose internal errors in production
  if (process.env.NODE_ENV === 'production' && status === 500) {
    message = 'Something went wrong';
  }
  
  // Send error response
  res.status(status).json({
    error: message,
    type: type,
    timestamp: new Date().toISOString(),
    requestId: req.get('X-Request-ID') || 'unknown',
    ...(process.env.NODE_ENV === 'development' && { 
      stack: err.stack,
      details: err 
    })
  });
}

module.exports = errorHandler;