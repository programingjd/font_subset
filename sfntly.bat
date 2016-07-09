@echo off 
pushd sfntly
git init
git remote add origin https://github.com/googlei18n/sfntly.git
git config core.sparsecheckout true
echo java >> .git/info/sparse-checkout
git pull --depth=1 origin master
popd
