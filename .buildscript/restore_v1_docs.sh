#!/bin/bash

# Commit b3205fa199a19d6fbf13ee5c8e0c3d6d2b15b05f contains
# Javadoc for Okio 1.x. Those should be present on 
# gh-pages and published along with the other website 
# content, but if for some reason they have to be re-added 
# to gh-pages - run this script locally.

set -ex

REPO="git@github.com:square/okio.git"	
DIR=temp-clone	

# Delete any existing temporary website clone	
rm -rf $DIR	

# Clone the current repo into temp folder	
git clone $REPO $DIR	

# Move working directory into temp folder	
cd $DIR

# Restore Javadocs from 1.x	
git checkout gh-pages	
git cherry-pick b3205fa199a19d6fbf13ee5c8e0c3d6d2b15b05f	
git push	

# Delete our temp folder	
cd ..	
rm -rf $DIR
