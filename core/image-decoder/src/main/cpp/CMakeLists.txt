cmake_minimum_required(VERSION 3.14)
project(imagedecoder C CXX ASM)
include(FetchContent)

# Bundled codec projects expect this standard install variable to be defined
# even though the Android library does not install host-facing artifacts.
set(CMAKE_INSTALL_DATAROOTDIR "share" CACHE PATH "Install data root")
set(CMAKE_INSTALL_DATADIR "share" CACHE PATH "Install data directory")
set(CMAKE_INSTALL_INCLUDEDIR "include" CACHE PATH "Install include directory")
set(CMAKE_INSTALL_LIBDIR "lib" CACHE PATH "Install library directory")
set(CMAKE_INSTALL_BINDIR "bin" CACHE PATH "Install binary directory")

set(CMAKE_BUILD_TYPE Release)

option(WITH_JPEG "Include JPEG decoder" ON)
option(WITH_PNG "Include PNG decoder" ON)
option(WITH_WEBP "Include WebP decoder" ON)
option(WITH_HEIF "Include HEIF decoder" ON)
option(WITH_AVIF "Include AVIF decoder" ON)
option(WITH_JXL "Include JXL decoder" ON)

add_library(imagedecoder SHARED
  java_stream.cpp
  java_wrapper.cpp
  java_objects.cpp
  borders.cpp
  row_convert.cpp
)

# NDK 27 still defaults Android ELF LOAD segments to 4 KiB. Keep this local
# native target compatible with Android 16 KiB page-size devices until the
# project can move to an NDK whose default linker page size is 16 KiB.
if(ANDROID)
  target_link_options(imagedecoder PRIVATE "-Wl,-z,max-page-size=16384")
endif()

# Reuse the source cache shared by the earlier ABI builds while keeping each
# ABI's generated build directories separate. This prevents every native ABI
# configure from cloning the same codec repositories again.
get_filename_component(deps "../_deps" REALPATH BASE_DIR "${CMAKE_BINARY_DIR}")
set(FETCHCONTENT_BASE_DIR "${CMAKE_BINARY_DIR}/_deps")
foreach(dep IN ITEMS dav1d lcms2 libde265 libheif libjpeg-turbo libjxl libpng libwebp)
  string(TOUPPER "${dep}" dep_upper)
  if(EXISTS "${deps}/${dep}-src")
    set("FETCHCONTENT_SOURCE_DIR_${dep_upper}" "${deps}/${dep}-src" CACHE PATH "Local Komikku FC codec source" FORCE)
  endif()
endforeach()

add_subdirectory(lcms)

if(WITH_JPEG)
  add_subdirectory(libjpeg-turbo)
  add_definitions(-DHAVE_LIBJPEG)
  target_sources(imagedecoder PRIVATE decoder_jpeg.cpp)
endif()
if(WITH_PNG)
  add_subdirectory(libpng)
  add_definitions(-DHAVE_LIBPNG)
  target_sources(imagedecoder PRIVATE decoder_png.cpp)
endif()
if(WITH_WEBP)
  add_subdirectory(libwebp)
  add_definitions(-DHAVE_LIBWEBP)
  target_sources(imagedecoder PRIVATE decoder_webp.cpp)
endif()
if(WITH_HEIF OR WITH_AVIF)
  add_subdirectory(libheif)
  add_definitions(-DHAVE_LIBHEIF)
  target_sources(imagedecoder PRIVATE decoder_heif.cpp)
endif()
if(WITH_JXL)
  add_subdirectory(libjxl)
  add_definitions(-DHAVE_LIBJXL)
  target_sources(imagedecoder PRIVATE decoder_jxl.cpp)
endif()

target_link_libraries(imagedecoder android jnigraphics log)
