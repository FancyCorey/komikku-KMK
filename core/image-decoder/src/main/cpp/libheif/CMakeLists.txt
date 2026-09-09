FetchContent_Declare(libheif
  GIT_REPOSITORY  https://github.com/strukturag/libheif
  GIT_TAG         v1.13.0
  PATCH_COMMAND   ${CMAKE_COMMAND} -E copy ${CMAKE_CURRENT_LIST_DIR}/gdk-pixbuf-CMakeLists.txt gdk-pixbuf/CMakeLists.txt # Avoid building gdk-pixbuf, we don't need it
  BINARY_DIR      build
  SUBBUILD_DIR    subbuild
)

if(WITH_HEIF)
  include(libde265.cmake)
endif()

if(WITH_AVIF)
  include(dav1d.cmake)
endif()

option(BUILD_SHARED_LIBS "" OFF)
option(WITH_EXAMPLES "" OFF)
option(WITH_DAV1D "" OFF)
option(WITH_LIBDE265 "" OFF)
option(WITH_X265 "" OFF)
option(WITH_AOM "" OFF)
option(WITH_RAV1E "" OFF)

FetchContent_MakeAvailable(libheif)

set(LIBHEIF_BINARY_DIR_ABS "${CMAKE_BINARY_DIR}/libheif/build")
if(TARGET heif)
  target_include_directories(heif PRIVATE
    "${CMAKE_BINARY_DIR}/dav1d-build/include"
    "${CMAKE_BINARY_DIR}/dav1d-build/include/dav1d"
    "${CMAKE_BINARY_DIR}/libheif/libde265-build"
    "${CMAKE_BINARY_DIR}/libheif/libde265-build/libde265"
  )
endif()
target_include_directories(imagedecoder PRIVATE ${LIBHEIF_BINARY_DIR_ABS} ${libheif_SOURCE_DIR})
target_link_libraries(imagedecoder heif)
