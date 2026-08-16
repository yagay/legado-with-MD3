package io.legado.app.enhance.explore.builder

import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.enhance.explore.model.ExploreMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModernExploreTreeControlTest {

    @Test
    fun `tree select controls use stable path keys`() {
        val rawJson = """
  [
    {"title":"男频","children":[
      {"title":"状态","type":"select","chars":["全部","连载"]}
    ]},
    {"title":"女频","children":[
      {"title":"状态","type":"select","chars":["全部","完结"]}
    ]}
  ]
        """.trimIndent()

        val result = ModernExploreClassificationEngine.classify(emptyList(), rawJson)
        assertEquals(ExploreMode.TREE, result.mode)

        val controls = ModernExploreControlExtractor.fromTreeRoot(result.nodes)
        assertEquals(2, controls.size)
        assertEquals("0.0", controls[0].sourceKey)
        assertEquals("1.0", controls[1].sourceKey)
        assertNotEquals(controls[0].sourceKey, controls[1].sourceKey)
    }

    @Test
    fun `nested native controls are preserved by canonical tree flattening`() {
        val rawJson = """
  [
    {"title":"工具","children":[
      {"title":"关键字","type":"text"},
      {"title":"刷新发现页","type":"button","action":"java.refreshExplore()"},
      {"title":"登录","type":"button","action":"java.login()"}
    ]}
  ]
        """.trimIndent()

        val result = ModernExploreClassificationEngine.classify(emptyList(), rawJson)
        val flat = ModernExploreControlExtractor.flattenOriginalKinds(result.nodes)
        val native = ModernExploreControlExtractor.extractNativeControls(flat)

        assertTrue(flat.any { it.title == "关键字" && it.type == ExploreKind.Type.text })
        assertTrue(flat.any { it.title == "刷新发现页" && it.type == ExploreKind.Type.button })
        assertTrue(native.visibleControls.any { it.title == "登录" })
    }
}
