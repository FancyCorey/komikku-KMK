FetchContent_Declare(libpng
  GIT_REPOSITORY  https://github.com/glennrp/libpng
  GIT_TAG         v1.6.42
  BINARY_DIR      build
  SUBBUILD_DIR    subbuild
)

option(PNG_SHARED "" OFF)
option(PNG_TOOLS "" OFF)
option(PNG_TESTS "" OFF)

FetchContent_MakeAvailable(libpng)

set(LIBPNG_BINARY_DIR_ABS "${CMAKE_BINARY_DIR}/libpng/build")
target_include_directories(imagedecoder PRIVATE ${LIBPNG_BINARY_DIR_ABS} ${libpng_SOURCE_DIR})
target_link_libraries(imagedecoder png_static z)
