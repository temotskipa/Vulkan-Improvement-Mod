package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

record SectionCaptureValidation(boolean valid, String reason) {
    static SectionCaptureValidation ok() {
        return new SectionCaptureValidation(true, "");
    }
    
    static SectionCaptureValidation invalid(String reason) {
        return new SectionCaptureValidation(false, reason);
    }
}