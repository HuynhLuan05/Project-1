#!/bin/bash
cd "$(dirname "$0")"

for chart in cart customer inventory order search tax media product storefront-bff backoffice-bff storefront-ui backoffice-ui; do
    echo "----------------------------------------"
    echo "Updating dependency for: $chart"
    echo "----------------------------------------"
    cd "$chart"
    helm dependency update .
    cd ..
done

echo "----------------------------------------"
echo "Updating dependency for: yas-dev"
echo "----------------------------------------"
cd yas-dev
helm dependency update .
cd ..
