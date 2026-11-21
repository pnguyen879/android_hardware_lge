//
// SPDX-FileCopyrightText: The LineageOS Project
// SPDX-License-Identifier: Apache-2.0
//

package vendor.lge.hardware.dac;

@VintfStability
@Backing(type="int")
enum Status {
    OK,
    NOT_SUPPORTED,
    INVALID_ARGUMENTS,
    INVALID_STATE,
}
