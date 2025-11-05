# resources/generate-keystore.sh
#!/bin/bash

echo "🔐 Generating SSL keystore for NetBattle Arena..."

# Generate keystore with self-signed certificate
keytool -genkeypair \
    -alias netbattle \
    -keyalg RSA \
    -keysize 2048 \
    -validity 365 \
    -keystore keystore.jks \
    -storepass netbattle123 \
    -keypass netbattle123 \
    -dname "CN=NetBattle Arena, OU=Gaming, O=NetBattle, L=City, ST=State, C=US"

echo "✅ Keystore generated: keystore.jks"
echo "   Password: netbattle123"
echo ""
echo "To view certificate info:"
echo "keytool -list -v -keystore keystore.jks -storepass netbattle123"