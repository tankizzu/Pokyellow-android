cmake_minimum_required(VERSION 3.22)
project(gbcore C)

# Motore di emulazione SameBoy (licenza MIT), copiato in third_party/SameBoy senza modifiche.
set(SAMEBOY_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/SameBoy)

set(SAMEBOY_SOURCES
    ${SAMEBOY_DIR}/Core/gb.c
    ${SAMEBOY_DIR}/Core/sgb.c
    ${SAMEBOY_DIR}/Core/apu.c
    ${SAMEBOY_DIR}/Core/memory.c
    ${SAMEBOY_DIR}/Core/mbc.c
    ${SAMEBOY_DIR}/Core/timing.c
    ${SAMEBOY_DIR}/Core/display.c
    ${SAMEBOY_DIR}/Core/camera.c
    ${SAMEBOY_DIR}/Core/sm83_cpu.c
    ${SAMEBOY_DIR}/Core/joypad.c
    ${SAMEBOY_DIR}/Core/save_state.c
    ${SAMEBOY_DIR}/Core/random.c
    ${SAMEBOY_DIR}/Core/rumble.c
)

add_library(gbcore SHARED
    gbcore_jni.c
    boot_rom.c
    extended_view.c
    game_tiles.c
    ${SAMEBOY_SOURCES}
)

target_include_directories(gbcore PRIVATE ${SAMEBOY_DIR}/Core ${SAMEBOY_DIR})

# Stesse opzioni usate dalla versione libretro di SameBoy: niente debugger, trucchi, riavvolgimento, orologio.
target_compile_definitions(gbcore PRIVATE
    _GNU_SOURCE
    _USE_MATH_DEFINES
    GB_DISABLE_TIMEKEEPING
    GB_DISABLE_REWIND
    GB_DISABLE_DEBUGGER
    GB_DISABLE_CHEATS
    GB_DISABLE_CHEAT_SEARCH
    "GB_VERSION=\"1.0.5\""
    "GB_COPYRIGHT_YEAR=\"2026\""
)

# I file del motore vanno compilati con GB_INTERNAL, come nel Makefile originale.
set_source_files_properties(${SAMEBOY_SOURCES} PROPERTIES COMPILE_DEFINITIONS GB_INTERNAL)

target_compile_options(gbcore PRIVATE -std=gnu11 -O2 -fno-strict-aliasing
    -Wno-unknown-warning-option -Wno-missing-braces -Wno-nonnull -Wno-unused-result
    -Wno-multichar -Wno-int-in-bool-context -Wno-nullability-completeness -Wno-deprecated-declarations)

target_link_libraries(gbcore PRIVATE log m)
