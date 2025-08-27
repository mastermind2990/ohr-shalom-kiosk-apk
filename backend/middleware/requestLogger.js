/**
 * Custom request logger middleware
 * 
 * Logs detailed information about incoming requests
 */
function requestLogger(req, res, next) {
  const startTime = Date.now();
  
  // Log request details
  console.log(`📥 ${req.method} ${req.originalUrl}`, {
    ip: req.ip,
    userAgent: req.get('User-Agent'),
    contentType: req.get('Content-Type'),
    contentLength: req.get('Content-Length'),
    timestamp: new Date().toISOString()
  });
  
  // Log response details when response finishes
  const originalSend = res.send;
  res.send = function(data) {
    const duration = Date.now() - startTime;
    
    console.log(`📤 ${req.method} ${req.originalUrl} - ${res.statusCode}`, {
      duration: `${duration}ms`,
      contentLength: data ? data.length : 0,
      timestamp: new Date().toISOString()
    });
    
    originalSend.call(this, data);
  };
  
  next();
}

module.exports = requestLogger;