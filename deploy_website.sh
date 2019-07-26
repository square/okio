#!/bin/bash

# The website is built using MkDocs with the Material theme.
# https://squidfunk.github.io/mkdocs-material/
# It requires Python to run.
# Install the packages with the following command:
# pip install mkdocs mkdocs-material

set -ex

REPO="git@github.com:square/okio.git"
DIR=temp-clone

# Delete any existing temporary website clone
rm -rf $DIR

# Clone the current repo into temp folder
git clone $REPO $DIR

# Move working directory into temp folder
cd $DIR

# Generate the API docs
./gradlew \
  :okio:dokka

# Copy in special files that GitHub wants in the project root.
cat README.md | grep -v 'project website' > docs/index.md
cp CHANGELOG.md docs/changelog.md
cp CONTRIBUTING.md docs/contributing.md

# Build the site and push the new files up to GitHub
mkdocs gh-deploy

# Restore Javadocs from 1.x
git checkout gh-pages
git cherry-pick b3205fa199a19d6fbf13ee5c8e0c3d6d2b15b05f
git push

# Delete our temp folder
cd ..
rm -rf $DIR
