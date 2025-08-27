# Ohr Shalom Kiosk Backend

Backend server for the Ohr Shalom donation kiosk Android application. Provides Stripe Terminal integration for NFC/Tap to Pay functionality.

## 🚀 Quick Setup on DigitalOcean Droplet

### Automated Installation

Run this single command on your Ubuntu 20.04/22.04 Droplet:

```bash
curl -sSL https://raw.githubusercontent.com/mastermind2990/ohr-shalom-kiosk-apk/droplet-backend/backend/scripts/setup-droplet.sh | bash
```

This will automatically:
- ✅ Install Node.js 18, PM2, and Nginx
- ✅ Configure firewall and security settings
- ✅ Clone and setup the application
- ✅ Configure SSL (optional)
- ✅ Start the backend service

### Manual Setup Steps

If you prefer manual installation:

1. **Update system and install dependencies:**
   ```bash
   sudo apt update && sudo apt upgrade -y
   sudo apt install -y curl wget git ufw nginx
   ```

2. **Install Node.js 18:**
   ```bash
   curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
   sudo apt install -y nodejs
   ```

3. **Install PM2:**
   ```bash
   sudo npm install -g pm2@latest
   ```

4. **Clone repository:**
   ```bash
   git clone -b droplet-backend https://github.com/mastermind2990/ohr-shalom-kiosk-apk.git /var/www/ohr-shalom-backend
   cd /var/www/ohr-shalom-backend/backend
   ```

5. **Install dependencies:**
   ```bash
   npm install --production
   ```

6. **Configure environment:**
   ```bash
   cp .env.example .env
   nano .env  # Edit with your Stripe credentials
   ```

7. **Start with PM2:**
   ```bash
   pm2 start ecosystem.config.js
   pm2 save
   ```

## 🔐 Stripe Configuration

### Required Stripe Credentials

You need these from your [Stripe Dashboard](https://dashboard.stripe.com/):

1. **Stripe Secret Key** (`sk_test_...` or `sk_live_...`)
   - Get from: Dashboard → Developers → API Keys
   - Add to `.env` file: `STRIPE_SECRET_KEY=sk_test_your_key_here`

2. **Stripe Terminal Location ID** (`tml_...`)
   - Create via API or Dashboard → Terminal → Locations
   - Add to `.env` file: `STRIPE_LOCATION_ID=tml_your_location_id`

### Creating a Stripe Terminal Location

You can create a location using the API:

```bash
curl -X POST https://api.stripe.com/v1/terminal/locations \
  -H "Authorization: Bearer sk_test_your_secret_key" \
  -d "display_name=Ohr Shalom Synagogue" \
  -d "address[line1]=123 Synagogue Ave" \
  -d "address[city]=Your City" \
  -d "address[state]=FL" \
  -d "address[postal_code]=12345" \
  -d "address[country]=US"
```

Or use the backend API after setup:

```bash
curl -X POST http://your-server.com/api/stripe/locations \
  -H "Content-Type: application/json" \
  -d '{
    "display_name": "Ohr Shalom Synagogue",
    "address": {
      "line1": "123 Synagogue Ave",
      "city": "Your City",
      "state": "FL",
      "postal_code": "12345",
      "country": "US"
    }
  }'
```

## 📡 API Endpoints

### Core Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/` | GET | API information and status |
| `/health` | GET | Health check for monitoring |
| `/api/stripe/connection_token` | POST | Generate Stripe Terminal connection token |
| `/api/stripe/payment_intent` | POST | Create payment intent for Terminal |
| `/api/stripe/locations` | GET | List Terminal locations |
| `/api/stripe/locations` | POST | Create new Terminal location |
| `/api/stripe/config` | GET | Get Stripe configuration info |

### Android App Configuration

Use these endpoints in your Android app:

- **Connection Token Endpoint:** `http://your-server.com/api/stripe/connection_token`
- **Payment Intent Endpoint:** `http://your-server.com/api/stripe/payment_intent`

## 🛠️ Management Commands

### PM2 Process Management

```bash
# View application status
pm2 status

# View real-time logs
pm2 logs ohr-shalom-backend

# Restart application
pm2 restart ohr-shalom-backend

# Stop application
pm2 stop ohr-shalom-backend

# Start application
pm2 start ecosystem.config.js

# Save PM2 configuration
pm2 save
```

### Nginx Management

```bash
# Test Nginx configuration
sudo nginx -t

# Reload Nginx
sudo systemctl reload nginx

# Restart Nginx
sudo systemctl restart nginx

# View Nginx status
sudo systemctl status nginx
```

### Application Logs

```bash
# View application logs
tail -f /var/www/ohr-shalom-backend/backend/logs/combined.log

# View error logs
tail -f /var/www/ohr-shalom-backend/backend/logs/error.log

# View PM2 logs
pm2 logs ohr-shalom-backend --lines 100
```

## 🔒 Security Features

- **CORS Protection:** Configured for Android app access
- **Rate Limiting:** 100 requests per 15 minutes per IP
- **Security Headers:** Helmet.js security middleware
- **Input Validation:** Request validation and sanitization
- **Firewall:** UFW configured with minimal required ports
- **SSL/TLS:** Optional Let's Encrypt integration

## 🧪 Testing the Backend

### Health Check

```bash
curl http://your-server.com/health
```

Expected response:
```json
{
  "status": "healthy",
  "timestamp": "2024-01-01T00:00:00.000Z",
  "uptime": "1h 30m 45s",
  "environment": "production",
  "stripe": {
    "configured": true,
    "mode": "test"
  }
}
```

### Connection Token Test

```bash
curl -X POST http://your-server.com/api/stripe/connection_token
```

Expected response:
```json
{
  "secret": "pst_test_1234567890...",
  "created": 1640995200,
  "livemode": false
}
```

### Payment Intent Test

```bash
curl -X POST http://your-server.com/api/stripe/payment_intent \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 1800,
    "currency": "usd",
    "description": "Test Chai Donation"
  }'
```

## 🚨 Troubleshooting

### Common Issues

1. **"Stripe not configured" error**
   - Check `.env` file has correct `STRIPE_SECRET_KEY`
   - Restart PM2: `pm2 restart ohr-shalom-backend`

2. **Connection refused**
   - Check if PM2 is running: `pm2 status`
   - Check firewall: `sudo ufw status`
   - Check Nginx: `sudo systemctl status nginx`

3. **SSL certificate issues**
   - Renew certificates: `sudo certbot renew`
   - Check certificate status: `sudo certbot certificates`

4. **Memory issues**
   - Monitor usage: `pm2 monit`
   - Check logs: `pm2 logs ohr-shalom-backend`

### Log Locations

- **Application logs:** `/var/www/ohr-shalom-backend/backend/logs/`
- **PM2 logs:** `~/.pm2/logs/`
- **Nginx logs:** `/var/log/nginx/`
- **System logs:** `/var/log/syslog`

## 📱 Android App Integration

### Update Android Configuration

In your Android app admin interface, set:

1. **Connection Token Endpoint:** `http://your-server-ip/api/stripe/connection_token`
2. **Stripe Environment:** Match your secret key (test/live)
3. **Location ID:** Use the `tml_...` ID from your Stripe location

### Test Integration

1. Open admin interface in Android app (tap logo 5 times, PIN: 12345)
2. Go to Stripe Configuration section
3. Enter your server's connection token endpoint
4. Click "Validate Credentials"
5. Test payment functionality

## 🔄 Updates and Maintenance

### Updating the Backend

```bash
cd /var/www/ohr-shalom-backend
git pull origin droplet-backend
cd backend
npm install --production
pm2 restart ohr-shalom-backend
```

### Backup Configuration

```bash
# Backup .env file
cp /var/www/ohr-shalom-backend/backend/.env ~/backup-env-$(date +%Y%m%d)

# Backup PM2 configuration
pm2 save
```

## 📞 Support

For issues or questions:

1. Check the logs first
2. Verify Stripe credentials
3. Test health endpoint
4. Check firewall and network connectivity
5. Review this documentation

## 🏗️ Architecture

```
Internet → Nginx (Port 80/443) → Node.js App (Port 3000) → Stripe API
                                         ↓
                               Android Kiosk App (NFC Payments)
```

The backend serves as a secure bridge between your Android kiosk and Stripe's payment processing infrastructure, handling authentication tokens and payment intents while maintaining PCI compliance.