#!/usr/bin/env python3
"""
Stripe Terminal Reader Registration Script
Register an Android tablet as a Stripe Terminal reader for Tap to Pay
"""

import requests
import json
import sys

# Stripe API Configuration
STRIPE_SECRET_KEY = "sk_live_YOUR_SECRET_KEY_HERE"  # Replace with your live secret key
LOCATION_ID = "tml_GKsXoQ8u9cFZJF"
STRIPE_API_URL = "https://api.stripe.com/v1"

def register_terminal_reader():
    """Register the tablet as a Stripe Terminal reader"""
    
    headers = {
        "Authorization": f"Bearer {STRIPE_SECRET_KEY}",
        "Content-Type": "application/x-www-form-urlencoded"
    }
    
    # Reader registration data
    data = {
        "type": "tap_to_pay_android",
        "location": LOCATION_ID,
        "label": "Ohr Shalom Donation Kiosk",
        "metadata[tablet_id]": "ohr-shalom-tablet-001",
        "metadata[app_version]": "1.8.3-terminal-fixed",
        "metadata[purpose]": "donation_kiosk"
    }
    
    print("🔄 Registering tablet as Terminal reader...")
    print(f"📍 Location: {LOCATION_ID}")
    print(f"🏷️  Label: {data['label']}")
    
    try:
        response = requests.post(
            f"{STRIPE_API_URL}/terminal/readers",
            headers=headers,
            data=data
        )
        
        if response.status_code == 200:
            reader = response.json()
            print("✅ Terminal reader registered successfully!")
            print(f"📱 Reader ID: {reader['id']}")
            print(f"🔗 Device Type: {reader['device_type']}")
            print(f"📍 Location: {reader['location']}")
            print(f"📊 Status: {reader['status']}")
            return reader
            
        else:
            print(f"❌ Registration failed: HTTP {response.status_code}")
            print(f"Error: {response.text}")
            return None
            
    except Exception as e:
        print(f"❌ Error registering reader: {e}")
        return None

def list_existing_readers():
    """List existing Terminal readers"""
    
    headers = {
        "Authorization": f"Bearer {STRIPE_SECRET_KEY}",
    }
    
    try:
        response = requests.get(
            f"{STRIPE_API_URL}/terminal/readers",
            headers=headers
        )
        
        if response.status_code == 200:
            readers = response.json()
            print(f"\n📋 Found {len(readers['data'])} existing readers:")
            
            for reader in readers['data']:
                print(f"  📱 {reader['id']} - {reader['label']} ({reader['device_type']})")
                print(f"     Location: {reader['location']} - Status: {reader['status']}")
            
            return readers['data']
        else:
            print(f"❌ Failed to list readers: HTTP {response.status_code}")
            return []
            
    except Exception as e:
        print(f"❌ Error listing readers: {e}")
        return []

def verify_location():
    """Verify the location exists and get details"""
    
    headers = {
        "Authorization": f"Bearer {STRIPE_SECRET_KEY}",
    }
    
    try:
        response = requests.get(
            f"{STRIPE_API_URL}/terminal/locations/{LOCATION_ID}",
            headers=headers
        )
        
        if response.status_code == 200:
            location = response.json()
            print(f"✅ Location verified: {location['display_name']}")
            print(f"📍 Address: {location['address']['line1']}, {location['address']['city']}")
            return location
        else:
            print(f"❌ Location verification failed: HTTP {response.status_code}")
            return None
            
    except Exception as e:
        print(f"❌ Error verifying location: {e}")
        return None

if __name__ == "__main__":
    print("🚀 Stripe Terminal Reader Registration")
    print("=" * 50)
    
    if STRIPE_SECRET_KEY == "sk_live_YOUR_SECRET_KEY_HERE":
        print("❌ Please update STRIPE_SECRET_KEY in the script with your actual secret key")
        sys.exit(1)
    
    # Step 1: Verify location
    print("\n1️⃣ Verifying location...")
    location = verify_location()
    if not location:
        print("❌ Cannot proceed without valid location")
        sys.exit(1)
    
    # Step 2: List existing readers
    print("\n2️⃣ Checking existing readers...")
    existing_readers = list_existing_readers()
    
    # Step 3: Register new reader
    print("\n3️⃣ Registering tablet as Terminal reader...")
    reader = register_terminal_reader()
    
    if reader:
        print(f"\n🎯 Next Steps:")
        print(f"1. The tablet should now appear in your Stripe Dashboard under Terminal > Readers")
        print(f"2. Reader ID: {reader['id']}")
        print(f"3. Test NFC payment processing on the tablet")
        print(f"4. Check Terminal status in the admin interface")
    else:
        print("\n❌ Registration failed. Please check your Stripe credentials and try again.")