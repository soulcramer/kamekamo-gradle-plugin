#/bin/env sh

cd kamekamo-plugin
swiftc thermal_state.swift -emit-library
mv -f libthermal_state.dylib src/main/resources/libthermal_state.dylib
cd ..