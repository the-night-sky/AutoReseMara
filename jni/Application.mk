APP_STL := gnustl_static
APP_CPPFLAGS := -frtti -fexceptions
# 対応するCPU、複数指定する場合は例えばこのようにする、APP_ABI := armeabi armeabi-v7a x86 
# 複数指定すればするほど、対応端末は増えますが、APKの容量も増えます、OpenCVの場合は一つごとに６〜７MB増えます。
APP_ABI := armeabi armeabi-v7a mips x86
APP_PLATFORM := android-16