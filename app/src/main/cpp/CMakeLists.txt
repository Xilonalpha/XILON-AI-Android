cmake_minimum_required(VERSION 3.22.1)
project(xilon_native LANGUAGES CXX)

add_library(xilon_native SHARED gguf_probe.cpp)
target_compile_features(xilon_native PRIVATE cxx_std_17)
target_compile_options(xilon_native PRIVATE -Wall -Wextra -Werror)
