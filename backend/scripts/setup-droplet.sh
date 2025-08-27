#!/bin/bash

# Ohr Shalom Kiosk Backend - Droplet Setup Script
# This script sets up a complete backend environment on Ubuntu 20.04/22.04
# Run with: curl -sSL https://raw.githubusercontent.com/mastermind2990/ohr-shalom-kiosk-apk/droplet-backend/backend/scripts/setup-droplet.sh | bash

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
APP_NAME="ohr-shalom-backend"
APP_DIR="/var/www/$APP_NAME"
REPO_URL="https://github.com/mastermind2990/ohr-shalom-kiosk-apk.git"
BRANCH="droplet-backend"
USER=$(whoami)
NODE_VERSION="18"

# Functions
print_header() {
    echo -e "${PURPLE}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo -e "          🕍 OHR SHALOM KIOSK BACKEND SETUP 🕍"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo -e "${NC}"
}

print_section() {
    echo -e "\n${BLUE}▶ $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️ $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Check if running as root
check_root() {
    if [[ $EUID -eq 0 ]]; then
        print_error "This script should not be run as root"
        echo "Please run as a regular user with sudo privileges"
        exit 1
    fi
}

# Update system packages
update_system() {
    print_section "Updating system packages"
    sudo apt update -y
    sudo apt upgrade -y
    sudo apt install -y curl wget git ufw software-properties-common
    print_success "System updated"
}

# Install Node.js
install_nodejs() {
    print_section "Installing Node.js $NODE_VERSION"
    
    # Remove existing Node.js if any
    sudo apt remove -y nodejs npm || true
    
    # Install Node.js via NodeSource repository
    curl -fsSL https://deb.nodesource.com/setup_${NODE_VERSION}.x | sudo -E bash -
    sudo apt install -y nodejs
    
    # Verify installation
    node_version=$(node --version)
    npm_version=$(npm --version)
    
    print_success "Node.js installed: $node_version"
    print_success "npm installed: $npm_version"
}

# Install PM2
install_pm2() {
    print_section "Installing PM2 process manager"
    sudo npm install -g pm2@latest
    
    # Setup PM2 startup script
    sudo env PATH=$PATH:/usr/bin /usr/lib/node_modules/pm2/bin/pm2 startup systemd -u $USER --hp /home/$USER
    
    print_success "PM2 installed and configured"
}

# Setup firewall
setup_firewall() {
    print_section "Configuring firewall"
    
    # Reset firewall to defaults
    sudo ufw --force reset
    
    # Default policies
    sudo ufw default deny incoming
    sudo ufw default allow outgoing
    
    # Allow SSH (important!)
    sudo ufw allow ssh
    
    # Allow HTTP and HTTPS
    sudo ufw allow 80/tcp
    sudo ufw allow 443/tcp
    
    # Allow our app port (3000)
    sudo ufw allow 3000/tcp
    
    # Enable firewall
    sudo ufw --force enable
    
    print_success "Firewall configured"
    sudo ufw status
}

# Install Nginx
install_nginx() {
    print_section "Installing and configuring Nginx"
    
    sudo apt install -y nginx
    
    # Remove default site
    sudo rm -f /etc/nginx/sites-enabled/default
    
    # Create Nginx configuration
    sudo tee /etc/nginx/sites-available/$APP_NAME > /dev/null <<EOF
server {
    listen 80;
    server_name _;
    
    # Security headers
    add_header X-Content-Type-Options nosniff;
    add_header X-Frame-Options DENY;
    add_header X-XSS-Protection "1; mode=block";
    
    # Proxy to Node.js app
    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_cache_bypass \$http_upgrade;
        
        # Timeouts
        proxy_connect_timeout 30s;
        proxy_send_timeout 30s;
        proxy_read_timeout 30s;
    }
    
    # Health check endpoint (bypass proxy for direct access)
    location /health {
        proxy_pass http://127.0.0.1:3000/health;
        access_log off;
    }
}
EOF
    
    # Enable site
    sudo ln -sf /etc/nginx/sites-available/$APP_NAME /etc/nginx/sites-enabled/
    
    # Test Nginx configuration
    sudo nginx -t
    
    # Start and enable Nginx
    sudo systemctl start nginx
    sudo systemctl enable nginx
    
    print_success "Nginx installed and configured"
}

# Clone and setup application
setup_application() {
    print_section "Setting up application"
    
    # Create app directory
    sudo mkdir -p $APP_DIR
    sudo chown $USER:$USER $APP_DIR
    
    # Clone repository
    if [ -d "$APP_DIR/.git" ]; then
        print_warning "Repository already exists, pulling latest changes"
        cd $APP_DIR
        git fetch origin
        git checkout $BRANCH
        git pull origin $BRANCH
    else
        git clone -b $BRANCH $REPO_URL $APP_DIR
        cd $APP_DIR
    fi
    
    # Navigate to backend directory
    cd $APP_DIR/backend
    
    # Install dependencies
    npm install --production
    
    # Create logs directory
    mkdir -p logs
    
    # Create environment file from example
    if [ ! -f .env ]; then
        cp .env.example .env
        print_warning "Created .env file from template - YOU MUST EDIT IT!"
    fi
    
    print_success "Application setup complete"
}

# Prompt for Stripe configuration
configure_stripe() {
    print_section "Stripe Configuration Setup"
    
    echo -e "${CYAN}"
    echo "You need to configure your Stripe credentials."
    echo "Visit: https://dashboard.stripe.com/apikeys"
    echo -e "${NC}"
    
    read -p "Enter your Stripe Secret Key (sk_test_... or sk_live_...): " stripe_key
    
    if [ -n "$stripe_key" ]; then
        # Update .env file
        cd $APP_DIR/backend
        sed -i "s/STRIPE_SECRET_KEY=.*/STRIPE_SECRET_KEY=$stripe_key/" .env
        print_success "Stripe secret key configured"
        
        echo -e "${YELLOW}"
        echo "📍 You'll need a Stripe Terminal Location ID."
        echo "You can create one using the API or from Stripe Dashboard > Terminal > Locations"
        echo "For now, we'll use a placeholder - update it later in the .env file"
        echo -e "${NC}"
    else
        print_warning "Stripe key not provided - update .env file manually later"
    fi
}

# Start application with PM2
start_application() {
    print_section "Starting application with PM2"
    
    cd $APP_DIR/backend
    
    # Stop existing process if running
    pm2 stop $APP_NAME 2>/dev/null || true
    pm2 delete $APP_NAME 2>/dev/null || true
    
    # Start application
    pm2 start ecosystem.config.js
    
    # Save PM2 configuration
    pm2 save
    
    # Show status
    pm2 status
    
    print_success "Application started with PM2"
}

# Setup SSL with Let's Encrypt (optional)
setup_ssl() {
    print_section "SSL Setup (Optional)"
    
    echo -e "${YELLOW}"
    read -p "Do you want to setup SSL with Let's Encrypt? (y/N): " setup_ssl
    echo -e "${NC}"
    
    if [[ $setup_ssl =~ ^[Yy]$ ]]; then
        read -p "Enter your domain name (e.g., api.yourchurch.com): " domain_name
        
        if [ -n "$domain_name" ]; then
            # Install Certbot
            sudo apt install -y certbot python3-certbot-nginx
            
            # Update Nginx config with domain
            sudo sed -i "s/server_name _;/server_name $domain_name;/" /etc/nginx/sites-available/$APP_NAME
            sudo nginx -t && sudo systemctl reload nginx
            
            # Get SSL certificate
            sudo certbot --nginx -d $domain_name --non-interactive --agree-tos --email admin@$domain_name
            
            print_success "SSL certificate installed for $domain_name"
        fi
    fi
}

# Display final information
show_completion_info() {
    print_section "🎉 Installation Complete!"
    
    # Get server IP
    SERVER_IP=$(curl -s http://ifconfig.me || curl -s http://ipinfo.io/ip || echo "YOUR_SERVER_IP")
    
    echo -e "${GREEN}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "               🚀 OHR SHALOM BACKEND IS READY! 🚀"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo
    echo "📡 API Endpoints:"
    echo "   • Backend API: http://$SERVER_IP/"
    echo "   • Health Check: http://$SERVER_IP/health"
    echo "   • Connection Token: http://$SERVER_IP/api/stripe/connection_token"
    echo
    echo "🔧 Management Commands:"
    echo "   • View logs: pm2 logs $APP_NAME"
    echo "   • Restart: pm2 restart $APP_NAME"
    echo "   • Stop: pm2 stop $APP_NAME"
    echo "   • Status: pm2 status"
    echo
    echo "📁 Important Files:"
    echo "   • App Directory: $APP_DIR/backend"
    echo "   • Environment: $APP_DIR/backend/.env"
    echo "   • Nginx Config: /etc/nginx/sites-available/$APP_NAME"
    echo "   • Logs: $APP_DIR/backend/logs/"
    echo
    echo "⚙️ Next Steps:"
    echo "   1. Edit $APP_DIR/backend/.env with your Stripe credentials"
    echo "   2. Create a Stripe Terminal Location (see README)"
    echo "   3. Update your Android app with this Connection Token Endpoint:"
    echo "      http://$SERVER_IP/api/stripe/connection_token"
    echo "   4. Test the health endpoint: curl http://$SERVER_IP/health"
    echo
    echo -e "${NC}"
    
    print_warning "IMPORTANT: Edit the .env file to add your actual Stripe credentials!"
    echo -e "${CYAN}sudo nano $APP_DIR/backend/.env${NC}"
}

# Main execution
main() {
    print_header
    
    check_root
    update_system
    install_nodejs
    install_pm2
    setup_firewall
    install_nginx
    setup_application
    configure_stripe
    start_application
    setup_ssl
    show_completion_info
}

# Run main function
main "$@"