FetchContent_Declare(dav1d
  GIT_REPOSITORY    https://github.com/videolan/dav1d.git
  GIT_TAG           1.4.0
  CONFIGURE_COMMAND ""
  BUILD_COMMAND     ""
  BINARY_DIR        dav1d-build
  SUBBUILD_DIR      dav1d-subbuild
)
FetchContent_MakeAvailable(dav1d)

get_filename_component(DAV1D_BINARY_DIR_ABS "${dav1d_BINARY_DIR}" ABSOLUTE BASE_DIR "${CMAKE_BINARY_DIR}")
set(DAV1D_FILENAME "${DAV1D_BINARY_DIR_ABS}/src/libdav1d.a")

if(NOT EXISTS "${DAV1D_FILENAME}")
  set(ENV{ANDROID_NDK} ${CMAKE_ANDROID_NDK})
  find_program(DAV1D_MESON_EXECUTABLE NAMES meson meson.exe
    HINTS "$ENV{USERPROFILE}/AppData/Roaming/Python/Python312/Scripts" REQUIRED)
  set(DAV1D_NINJA_EXECUTABLE "${CMAKE_MAKE_PROGRAM}")
  set(ENV{NINJA} "${DAV1D_NINJA_EXECUTABLE}")
  if(CMAKE_HOST_WIN32)
    get_filename_component(DAV1D_NINJA_DIRECTORY "${DAV1D_NINJA_EXECUTABLE}" DIRECTORY)
    set(ENV{PATH} "${DAV1D_NINJA_DIRECTORY};$ENV{PATH}")
  endif()

  file(TO_CMAKE_PATH "${CMAKE_ANDROID_NDK}/toolchains/llvm/prebuilt/windows-x86_64" DAV1D_LLVM_ROOT)
  file(TO_CMAKE_PATH "${DAV1D_LLVM_ROOT}/sysroot" DAV1D_SYSROOT)
  set(DAV1D_CLANG "${DAV1D_LLVM_ROOT}/bin/clang.exe")
  set(DAV1D_CLANGXX "${DAV1D_LLVM_ROOT}/bin/clang++.exe")
  set(DAV1D_AR "${DAV1D_LLVM_ROOT}/bin/llvm-ar.exe")
  set(DAV1D_STRIP "${DAV1D_LLVM_ROOT}/bin/llvm-strip.exe")

  if(CMAKE_ANDROID_ARCH STREQUAL "arm")
    set(DAV1D_TRIPLET "armv7a-linux-androideabi")
    set(DAV1D_CPU_FAMILY "arm")
  elseif(CMAKE_ANDROID_ARCH STREQUAL "arm64")
    set(DAV1D_TRIPLET "aarch64-linux-android")
    set(DAV1D_CPU_FAMILY "aarch64")
  elseif(CMAKE_ANDROID_ARCH STREQUAL "x86")
    set(DAV1D_TRIPLET "i686-linux-android")
    set(DAV1D_CPU_FAMILY "x86")
  elseif(CMAKE_ANDROID_ARCH STREQUAL "x86_64")
    set(DAV1D_TRIPLET "x86_64-linux-android")
    set(DAV1D_CPU_FAMILY "x86_64")
  else()
    message(FATAL_ERROR "Unsupported Android architecture for dav1d: ${CMAKE_ANDROID_ARCH}")
  endif()

  file(MAKE_DIRECTORY "${DAV1D_BINARY_DIR_ABS}")
  file(WRITE "${DAV1D_BINARY_DIR_ABS}/android_cross.txt"
    "[binaries]\n"
    "c = ['${DAV1D_CLANG}', '--target=${DAV1D_TRIPLET}21', '--sysroot=${DAV1D_SYSROOT}']\n"
    "cpp = ['${DAV1D_CLANGXX}', '--target=${DAV1D_TRIPLET}21', '--sysroot=${DAV1D_SYSROOT}']\n"
    "ar = '${DAV1D_AR}'\n"
    "ld = '${DAV1D_CLANG}'\n"
    "strip = '${DAV1D_STRIP}'\n\n"
    "[properties]\n"
    "needs_exe_wrapper = true\n\n"
    "[host_machine]\n"
    "system = 'linux'\n"
    "cpu_family = '${DAV1D_CPU_FAMILY}'\n"
    "cpu = '${DAV1D_CPU_FAMILY}'\n"
    "endian = 'little'\n")

  if(DEFINED ENV{JITPACK})
    # Why don't they have python 3 :(
    FetchContent_Declare(python
      URL https://github.com/kageiit/jitpack-python/releases/download/3.8/python-3.8-ubuntu-16.tar.gz
      BINARY_DIR        python-build
      SUBBUILD_DIR      python-subbuild
    )
    FetchContent_MakeAvailable(python)
    set(ENV{PATH} "${python_SOURCE_DIR}/bin:$ENV{PATH}")

    FetchContent_Declare(meson
      URL https://github.com/mesonbuild/meson/releases/download/0.58.0/meson-0.58.0.tar.gz
      BINARY_DIR        meson-build
      SUBBUILD_DIR      meson-subbuild
    )
    FetchContent_MakeAvailable(meson)
    file(RENAME ${meson_SOURCE_DIR}/meson.py ${meson_SOURCE_DIR}/meson)
    get_filename_component(ninja_PATH ${CMAKE_COMMAND} DIRECTORY)
    set(ENV{PATH} "${meson_SOURCE_DIR}:${ninja_PATH}:$ENV{PATH}")

    FetchContent_Declare(nasm
      URL http://mirrors.kernel.org/ubuntu/pool/universe/n/nasm/nasm_2.14.02-1_amd64.deb
      BINARY_DIR        nasm-build
      SUBBUILD_DIR      nasm-subbuild
      PATCH_COMMAND     bash -c "tar xvf data.tar.xz || true"
    )
    FetchContent_MakeAvailable(nasm)
    set(ENV{PATH} "${nasm_SOURCE_DIR}/usr/bin:$ENV{PATH}")
  endif()

  set(DAV1D_MESON_OPTIONS
    -Denable_tools=false
    -Denable_tests=false
  )
  if(CMAKE_HOST_WIN32)
    list(APPEND DAV1D_MESON_OPTIONS -Denable_asm=false)
  endif()

  execute_process(
    COMMAND "${DAV1D_MESON_EXECUTABLE}" setup
      "${DAV1D_BINARY_DIR_ABS}"
      "${dav1d_SOURCE_DIR}"
      --buildtype release
      --default-library static
      --cross-file "${DAV1D_BINARY_DIR_ABS}/android_cross.txt"
      ${DAV1D_MESON_OPTIONS}
    RESULT_VARIABLE CONFIG_DAV1D_RESULT
    OUTPUT_VARIABLE CONFIG_DAV1D_OUTPUT
    ERROR_VARIABLE CONFIG_DAV1D_ERROR
  )
  if(NOT CONFIG_DAV1D_RESULT EQUAL 0)
    message(FATAL_ERROR "dav1d Meson setup failed (${CONFIG_DAV1D_RESULT})\n${CONFIG_DAV1D_OUTPUT}\n${CONFIG_DAV1D_ERROR}")
  endif()
  execute_process(
    COMMAND "${DAV1D_MESON_EXECUTABLE}" compile -C "${DAV1D_BINARY_DIR_ABS}"
    RESULT_VARIABLE BUILD_DAV1D_RESULT
    OUTPUT_VARIABLE BUILD_DAV1D_OUTPUT
    ERROR_VARIABLE BUILD_DAV1D_ERROR
  )
  if(NOT BUILD_DAV1D_RESULT EQUAL 0)
    message(FATAL_ERROR "dav1d Meson compile failed (${BUILD_DAV1D_RESULT})\n${BUILD_DAV1D_OUTPUT}\n${BUILD_DAV1D_ERROR}")
  endif()
endif()

if(NOT EXISTS "${DAV1D_FILENAME}")
  message(FATAL_ERROR "libavif: ${DAV1D_FILENAME} is missing")
endif()

set(DAV1D_FOUND 1)
set(DAV1D_LIBRARIES "${DAV1D_FILENAME}")
set(DAV1D_INCLUDE_DIR
  "${dav1d_BINARY_DIR}"
  "${dav1d_BINARY_DIR}/include"
  "${dav1d_BINARY_DIR}/include/dav1d"
  "${dav1d_SOURCE_DIR}/include"
)
