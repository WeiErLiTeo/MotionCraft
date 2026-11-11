package com.example.core.zlc

/**
 * 各厂商动态照片 XMP 模板构造（1:1 完整照搬自 ZLC XmpTemplate）。
 */
object ZlcXmpTemplate {

    private const val GoogleHeadHdr: String = """
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/"
        xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
      hdrgm:Version="1.0"
      GCamera:MotionPhoto="1"
      GCamera:MotionPhotoVersion="1"
      GCamera:MotionPhotoPresentationTimestampUs="{pts}">

"""

    private const val GoogleHeadNonHdr: String = """
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
      GCamera:MotionPhoto="1"
      GCamera:MotionPhotoVersion="1"
      GCamera:MotionPhotoPresentationTimestampUs="{pts}">

"""

    private const val Tail: String = """
      </Container:Directory>
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>
"""

    private const val ItemPrimary: String = """
      <Container:Directory>
        <rdf:Seq>
          <rdf:li rdf:parseType="Resource">
            <Container:Item
              Item:Mime="image/jpeg"
              Item:Semantic="Primary"
              Item:Length="0"
              Item:Padding="0"/>
          </rdf:li>

"""

    private const val ItemGainmap: String = """
          <rdf:li rdf:parseType="Resource">
            <Container:Item
              Item:Mime="image/jpeg"
              Item:Semantic="GainMap"
              Item:Length="{gainmap_len}"
              Item:Padding="0"/>
          </rdf:li>

"""

    private const val ItemVideo: String = """
          <rdf:li rdf:parseType="Resource">
            <Container:Item
              Item:Mime="video/mp4"
              Item:Semantic="MotionPhoto"
              Item:Length="{video_len}"
              Item:Padding="0"/>
          </rdf:li>
        </rdf:Seq>

"""

    private const val VivoTailClose: String = """
        </rdf:Seq>

"""

    private const val VivoSingleHeadHdr: String = """
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        xmlns:hdrgm="http://ns.adobe.com/hdr-gain-map/1.0/"
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
        xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
        xmlns:VCamera="http://ns.vivo.com/photos/1.0/camera/"
        xmlns:Camera="http://ns.google.com/photos/1.0/camera/"
      hdrgm:Version="1.0"
      GCamera:MotionPhoto="1"
      GCamera:MotionPhotoVersion="1"
      GCamera:MotionPhotoPresentationTimestampUs="{pts}"
      VCamera:VMotionPhotoVersion="1"
      VCamera:VMediaKitVersion="1.0.0.9"
      Camera:MotionPhoto="1"
      Camera:MotionPhotoVersion="1"
      Camera:MotionPhotoPresentationTimestampUs="{pts}">
"""

    private const val VivoSingleHeadNonHdr: String = """
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.1.0-jc003">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
    <rdf:Description rdf:about=""
        xmlns:Container="http://ns.google.com/photos/1.0/container/"
        xmlns:Item="http://ns.google.com/photos/1.0/container/item/"
        xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
        xmlns:VCamera="http://ns.vivo.com/photos/1.0/camera/"
        xmlns:Camera="http://ns.google.com/photos/1.0/camera/"
      GCamera:MotionPhoto="1"
      GCamera:MotionPhotoVersion="1"
      GCamera:MotionPhotoPresentationTimestampUs="{pts}"
      VCamera:VMotionPhotoVersion="1"
      VCamera:VMediaKitVersion="1.0.0.9"
      Camera:MotionPhoto="1"
      Camera:MotionPhotoVersion="1"
      Camera:MotionPhotoPresentationTimestampUs="{pts}">
"""

    private const val VivoSingleItemPrimary: String = """
      <Container:Directory>
        <rdf:Seq>
          <rdf:li rdf:parseType="Resource">
            <Container:Item
              Item:Semantic="Primary"
              Item:Mime="image/jpeg"/>
          </rdf:li>
"""

    private const val VivoSingleItemGainmap: String = """
          <rdf:li rdf:parseType="Resource">
            <Container:Item
              Item:Semantic="GainMap"
              Item:Mime="image/jpeg"
              Item:Length="{gainmap_len}"/>
          </rdf:li>
"""

    private const val VivoSingleItemVideo: String = """
          <rdf:li rdf:parseType="Resource">
            <Container:Item
              Item:Mime="video/mp4"
              Item:Semantic="MotionPhoto"
              Item:Length="{video_len}"
              Item:Padding="0"/>
          </rdf:li>
        </rdf:Seq>
      </Container:Directory>
"""

    private const val VivoSingleTail: String = """
    </rdf:Description>
  </rdf:RDF>
</x:xmpmeta>"""

    fun buildGoogleXmp(ptsUs: Long, gainmapLen: Int?, videoLen: Int): String {
        val head = if (gainmapLen != null) GoogleHeadHdr else GoogleHeadNonHdr
        val parts = mutableListOf(head.replace("{pts}", ptsUs.toString()), ItemPrimary)
        if (gainmapLen != null) {
            parts.add(ItemGainmap.replace("{gainmap_len}", gainmapLen.toString()))
        }
        parts.add(ItemVideo.replace("{video_len}", videoLen.toString()))
        parts.add(Tail)
        return parts.joinToString("")
    }

    /** vivo 单文件实况：Google 容器 + VCamera 私有字段，MotionPhoto 恒为 1。 */
    fun buildVivoSingleXmp(ptsUs: Long, gainmapLen: Int?, videoLen: Int): String {
        val head = if (gainmapLen != null) VivoSingleHeadHdr else VivoSingleHeadNonHdr
        val parts = mutableListOf(head.replace("{pts}", ptsUs.toString()), VivoSingleItemPrimary)
        if (gainmapLen != null) {
            parts.add(VivoSingleItemGainmap.replace("{gainmap_len}", gainmapLen.toString()))
        }
        parts.add(VivoSingleItemVideo.replace("{video_len}", videoLen.toString()))
        parts.add(VivoSingleTail)
        return parts.joinToString("")
    }
}
