#
# SPDX-FileCopyrightText: The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
#

# Overlays
PRODUCT_PACKAGES += \
    FrameworksResOverlayLgeDualScreen

# DualScreen
PRODUCT_PACKAGES += \
    LGEDualScreen \
    vendor.lge.hardware.dualscreen-service

PRODUCT_PACKAGES += \
    Vendor_1004_Product_637a.idc \
    Vendor_1004_Product_637a.kl \
    display_settings.xml

PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.software.activities_on_secondary_displays.xml:$(TARGET_COPY_OUT_VENDOR)/etc/permissions/android.software.activities_on_secondary_displays.xml
