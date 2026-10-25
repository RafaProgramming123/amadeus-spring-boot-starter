#!/usr/bin/env bash
# One-time setup: replaces the "yourname" placeholder with your GitHub username.
#
#   ./rename.sh <github-username> "<Your Full Name>"
#   ./rename.sh andrej-r "Andrej R"
#
# - Maven groupId  -> io.github.<username>        (Maven Central verifies this via GitHub)
# - Java package   -> io.github.<username>.amadeus (hyphens become underscores: Java rule)
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "usage: $0 <github-username> \"<Your Full Name>\"" >&2
  exit 1
fi

user="$(echo "$1" | tr '[:upper:]' '[:lower:]')"
name="$2"
pkg="${user//-/_}"
cd "$(dirname "$0")"

files=$(grep -rl --exclude-dir=.git --exclude-dir=target --exclude=rename.sh -e 'yourname' -e 'Your Name' .)

for f in $files; do
  sed -i.bak \
    -e "s/io\.github\.yourname\.amadeus/io.github.${pkg}.amadeus/g" \
    -e "s/yourname/${user}/g" \
    -e "s/Your Name/${name}/g" \
    "$f"
  rm -f "$f.bak"
done

# move source folders io/github/yourname -> io/github/<pkg>
find . -type d -path '*/io/github/yourname' -not -path '*/target/*' | while read -r dir; do
  mv "$dir" "$(dirname "$dir")/${pkg}"
done

echo "Done: groupId io.github.${user}, package io.github.${pkg}.amadeus"
