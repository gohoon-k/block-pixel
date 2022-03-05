package kiwi.hoonkun.plugins.pixel.chunk.generator

import org.bukkit.generator.ChunkGenerator

class VoidChunkGenerator: ChunkGenerator() {

    override fun shouldGenerateBedrock(): Boolean = false
    override fun shouldGenerateDecorations(): Boolean = false
    override fun shouldGenerateCaves(): Boolean = false
    override fun shouldGenerateNoise(): Boolean = false
    override fun shouldGenerateMobs(): Boolean = false
    override fun shouldGenerateStructures(): Boolean = false
    override fun shouldGenerateSurface(): Boolean = false

}