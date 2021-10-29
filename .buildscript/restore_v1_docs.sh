#!/bin/bash

# Commit b3205fa199a19d6fbf13ee5c8e0c3d6d2b15b05f contains
# Javadoc for Okio 1.x. Those should be present on
# gh-pages and published along with the other website
# content, but if for some reason they have to be re-added
# to gh-pages - run this script locally.

set -ex

DIR=temp-clone

# Delete any existing temporary website clone
rm -rf $DIR

# Clone the current repo into temp folder
git clone . $DIR

# Move working directory into temp folder
cd $DIR

# Restore docs from 1.x
git checkout b3205fa199a19d6fbf13ee5c8e0c3d6d2b15b05f
mkdir -p ../site
mv ./1.x ../site/1.x

# Restore docs from 2.x
git checkout 9235ff8faca96082aa8784e789448b5f4893af69
mkdir -p ../site
mv ./2.x ../site/2.x

# Delete our temp folder
cd ..
rm -rf $DIR
