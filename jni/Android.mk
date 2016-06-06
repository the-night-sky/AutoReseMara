LOCAL_PATH := $(call my-dir)
 
include $(CLEAR_VARS)
 
# OpenCV
OPENCV_CAMERA_MODULES:=on
OPENCV_INSTALL_MODULES:=on
#OPENCV_LIB_TYPE:=STATIC
 
include ../../OpenCV-android-sdk/sdk/native/jni/OpenCV.mk