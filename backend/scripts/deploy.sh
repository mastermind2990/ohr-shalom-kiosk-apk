#!/bin/bash

# Ohr Shalom Kiosk Backend - Quick Deployment Script
# Use this to deploy updates to your running server

set -e

# Colors
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

APP_NAME="ohr-shalom-backend"
APP_DIR="/var/www/$APP_NAME"

echo -e "${BLUE}🚀 Deploying Ohr Shalom Backend Updates${NC}"

# Check if we're in the right directory
if [ ! -f "server.js" ]; then
    echo -e "${RED}❌ Error: Run this script from the backend directory${NC}"
    exit 1
fi

# Pull latest changes
echo -e "${YELLOW}📡 Pulling latest changes...${NC}"
git pull origin droplet-backend

# Install dependencies
echo -e "${YELLOW}📦 Installing dependencies...${NC}"
npm install --production

# Restart PM2
echo -e "${YELLOW}🔄 Restarting application...${NC}"
pm2 restart $APP_NAME

# Show status
echo -e "${YELLOW}📊 Application status:${NC}"
pm2 status

# Test health endpoint
echo -e "${YELLOW}🏥 Testing health endpoint...${NC}"
sleep 3
if curl -f http://localhost:3000/health > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Health check passed - deployment successful!${NC}"
else
    echo -e "${RED}❌ Health check failed - check logs: pm2 logs $APP_NAME${NC}"
    exit 1
fi

echo -e "${GREEN}🎉 Deployment complete!${NC}"