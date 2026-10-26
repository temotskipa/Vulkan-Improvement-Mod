package com.temotskipa.vulkanimprovement.client.vulkan.terrain;

record ResidentPageShortage(int pages, long meshlets, long vertexBytes, long indexBytes) {
    static final ResidentPageShortage NONE = new ResidentPageShortage(0, 0L, 0L, 0L);
    
    boolean any() {
        return this.pages > 0 || this.meshlets > 0L || this.vertexBytes > 0L || this.indexBytes > 0L;
    }
    
    ResidentPageShortage plus(int morePages, long moreMeshlets, long moreVertexBytes, long moreIndexBytes) {
        return new ResidentPageShortage(this.pages + morePages, this.meshlets + moreMeshlets, this.vertexBytes + moreVertexBytes, this.indexBytes + moreIndexBytes);
    }
}