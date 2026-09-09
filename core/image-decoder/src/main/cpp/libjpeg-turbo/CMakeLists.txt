FetchContent_Declare(libjpeg-turbo
  GIT_REPOSITORY  https://github.com/libjpeg-turbo/libjpeg-turbo
  GIT_TAG         2.1.90
  BINARY_DIR      build
  SUBBUILD_DIR    subbuild
)

option(WITH_JPEG8 "" 1)
option(WITH_TURBOJPEG "" 0)
option(ENABLE_SHARED "" 0)
option(REQUIRE_SIMD "" 1)

FetchContent_MakeAvailable(libjpeg-turbo)

set(LIBJPEG_BINARY_DIR_ABS "${CMAKE_BINARY_DIR}/libjpeg-turbo/build")
target_include_directories(imagedecoder PRIVATE ${LIBJPEG_BINARY_DIR_ABS} ${libjpeg-turbo_SOURCE_DIR})
target_link_libraries(imagedecoder jpeg-static)
