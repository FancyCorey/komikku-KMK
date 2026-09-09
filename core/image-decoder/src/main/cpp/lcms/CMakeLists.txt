FetchContent_Declare(lcms2
  GIT_REPOSITORY  https://github.com/mm2/Little-CMS
  GIT_TAG         lcms2.16
  PATCH_COMMAND   ${CMAKE_COMMAND} -E copy ${CMAKE_CURRENT_LIST_DIR}/lcms2-CMakeLists.txt CMakeLists.txt
  BINARY_DIR      build
  SUBBUILD_DIR    subbuild
)

set(LCMS_SOURCE_DIRS "${FETCHCONTENT_SOURCE_DIR_LCMS2}" "${CMAKE_BINARY_DIR}/_deps/lcms2-src")
foreach(LCMS_SOURCE_DIR IN LISTS LCMS_SOURCE_DIRS)
  if(EXISTS "${LCMS_SOURCE_DIR}" AND NOT EXISTS "${LCMS_SOURCE_DIR}/CMakeLists.txt")
    file(COPY "${CMAKE_CURRENT_LIST_DIR}/lcms2-CMakeLists.txt" DESTINATION "${LCMS_SOURCE_DIR}")
  endif()
endforeach()

FetchContent_MakeAvailable(lcms2)

if(NOT TARGET lcms2)
  add_library(lcms2 STATIC
    ${lcms2_SOURCE_DIR}/src/cmsalpha.c
    ${lcms2_SOURCE_DIR}/src/cmscam02.c
    ${lcms2_SOURCE_DIR}/src/cmscgats.c
    ${lcms2_SOURCE_DIR}/src/cmscnvrt.c
    ${lcms2_SOURCE_DIR}/src/cmserr.c
    ${lcms2_SOURCE_DIR}/src/cmshalf.c
    ${lcms2_SOURCE_DIR}/src/cmsgamma.c
    ${lcms2_SOURCE_DIR}/src/cmsgmt.c
    ${lcms2_SOURCE_DIR}/src/cmsintrp.c
    ${lcms2_SOURCE_DIR}/src/cmsio0.c
    ${lcms2_SOURCE_DIR}/src/cmsio1.c
    ${lcms2_SOURCE_DIR}/src/cmslut.c
    ${lcms2_SOURCE_DIR}/src/cmsmd5.c
    ${lcms2_SOURCE_DIR}/src/cmsmtrx.c
    ${lcms2_SOURCE_DIR}/src/cmsnamed.c
    ${lcms2_SOURCE_DIR}/src/cmsopt.c
    ${lcms2_SOURCE_DIR}/src/cmspack.c
    ${lcms2_SOURCE_DIR}/src/cmspcs.c
    ${lcms2_SOURCE_DIR}/src/cmsplugin.c
    ${lcms2_SOURCE_DIR}/src/cmsps2.c
    ${lcms2_SOURCE_DIR}/src/cmssamp.c
    ${lcms2_SOURCE_DIR}/src/cmssm.c
    ${lcms2_SOURCE_DIR}/src/cmstypes.c
    ${lcms2_SOURCE_DIR}/src/cmsvirt.c
    ${lcms2_SOURCE_DIR}/src/cmswtpnt.c
    ${lcms2_SOURCE_DIR}/src/cmsxform.c
  )
  target_include_directories(lcms2 PUBLIC ${lcms2_SOURCE_DIR}/include)
endif()

set(LCMS_BINARY_DIR_ABS "${CMAKE_BINARY_DIR}/lcms/build")
target_include_directories(imagedecoder PRIVATE ${LCMS_BINARY_DIR_ABS} ${lcms2_SOURCE_DIR})
target_link_libraries(imagedecoder lcms2)
