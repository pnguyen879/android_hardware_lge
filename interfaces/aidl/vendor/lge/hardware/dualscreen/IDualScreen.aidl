//
// SPDX-FileCopyrightText: The LineageOS Project
// SPDX-License-Identifier: Apache-2.0
//

package vendor.lge.hardware.dualscreen;

import vendor.lge.hardware.dualscreen.CaseInfo;
import vendor.lge.hardware.dualscreen.ICaseCallback;

@VintfStability
interface IDualScreen {
    void setCallback(in @nullable ICaseCallback callback);
    void setDisplayState(boolean interactive, int brightness);
    void drawCover(int width, int height, in byte[] pixels);
}
