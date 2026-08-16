from pathlib import Path

contract = Path('app/src/main/java/io/legado/app/ui/book/info/BookInfoContract.kt')
text = contract.read_text()
old = '    data class BookReviewImageClick(val imageUrl: String) : BookInfoIntent\n    data object RemarkClick : BookInfoIntent'
new = '    data class BookReviewImageClick(val imageUrl: String) : BookInfoIntent\n    data class BookReviewAudioClick(val audioUrl: String) : BookInfoIntent\n    data object RemarkClick : BookInfoIntent'
if old not in text: raise SystemExit('intent anchor missing')
text = text.replace(old, new, 1)
old = '    data class OpenFile(val uri: Uri, val mimeType: String) : BookInfoEffect\n    data class RunSourceCallback('
new = '    data class OpenFile(val uri: Uri, val mimeType: String) : BookInfoEffect\n    data class PlayBookReviewAudio(val audioUrl: String, val source: BookSource) : BookInfoEffect\n    data class RunSourceCallback('
if old not in text: raise SystemExit('effect anchor missing')
contract.write_text(text)

vm = Path('app/src/main/java/io/legado/app/ui/book/info/BookInfoViewModel.kt')
text = vm.read_text()
old = '''            is BookInfoIntent.BookReviewImageClick -> showDialog(BookInfoDialog.PhotoPreview(intent.imageUrl))\n            BookInfoIntent.RemarkClick ->'''
new = '''            is BookInfoIntent.BookReviewImageClick -> showDialog(BookInfoDialog.PhotoPreview(intent.imageUrl))\n            is BookInfoIntent.BookReviewAudioClick -> bookSource?.let { source ->\n                emitEffect(BookInfoEffect.PlayBookReviewAudio(intent.audioUrl, source))\n            }\n            BookInfoIntent.RemarkClick ->'''
if old not in text: raise SystemExit('vm intent anchor missing')
vm.write_text(text.replace(old, new, 1))

screen = Path('app/src/main/java/io/legado/app/ui/book/info/BookInfoScreen.kt')
text = screen.read_text()
old = '''            onLoadMore = { onIntent(BookInfoIntent.LoadMoreBookReviews) },\n            onImageClick = { onIntent(BookInfoIntent.BookReviewImageClick(it)) },\n        )'''
new = '''            onLoadMore = { onIntent(BookInfoIntent.LoadMoreBookReviews) },\n            onImageClick = { onIntent(BookInfoIntent.BookReviewImageClick(it)) },\n            onAudioClick = { onIntent(BookInfoIntent.BookReviewAudioClick(it)) },\n        )'''
if old not in text: raise SystemExit('screen anchor missing')
screen.write_text(text.replace(old, new, 1))

ui = Path('app/src/main/java/io/legado/app/ui/book/info/BookInfoReviewUi.kt')
text = ui.read_text()
text = text.replace('import androidx.compose.material.icons.outlined.Person\n', 'import androidx.compose.material.icons.outlined.Person\nimport androidx.compose.material.icons.outlined.PlayCircleOutline\n')
old = '''    onLoadMore: () -> Unit,\n    onImageClick: (String) -> Unit,\n) {'''
new = '''    onLoadMore: () -> Unit,\n    onImageClick: (String) -> Unit,\n    onAudioClick: (String) -> Unit,\n) {'''
if old not in text: raise SystemExit('ui sheet args missing')
text = text.replace(old, new, 1)
text = text.replace('BookReviewItem(item = item, onImageClick = onImageClick)', 'BookReviewItem(item = item, onImageClick = onImageClick, onAudioClick = onAudioClick)', 1)
old = '''private fun BookReviewItem(\n    item: BookReviewItemUi,\n    onImageClick: (String) -> Unit,\n) {'''
new = '''private fun BookReviewItem(\n    item: BookReviewItemUi,\n    onImageClick: (String) -> Unit,\n    onAudioClick: (String) -> Unit,\n) {'''
if old not in text: raise SystemExit('item args missing')
text = text.replace(old, new, 1)
anchor = '''        item.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->\n            AsyncImage(\n                model = imageUrl,\n                contentDescription = "评论图片",\n                contentScale = ContentScale.Fit,\n                modifier = Modifier\n                    .padding(start = 46.dp, top = 8.dp)\n                    .fillMaxWidth()\n                    .heightIn(max = 280.dp)\n                    .clip(RoundedCornerShape(12.dp))\n                    .clickable { onImageClick(imageUrl) },\n            )\n        }\n'''
addition = anchor + '''        item.audioUrl?.takeIf { it.isNotBlank() }?.let { audioUrl ->\n            TextButton(\n                onClick = { onAudioClick(audioUrl) },\n                modifier = Modifier.padding(start = 34.dp, top = 2.dp),\n            ) {\n                Icon(Icons.Outlined.PlayCircleOutline, contentDescription = null)\n                Text("播放语音", modifier = Modifier.padding(start = 6.dp))\n            }\n        }\n'''
if anchor not in text: raise SystemExit('main image anchor missing')
text = text.replace(anchor, addition, 1)
text = text.replace('ReviewReplyItem(reply, onImageClick)', 'ReviewReplyItem(reply, onImageClick, onAudioClick)', 1)
old = 'private fun ReviewReplyItem(reply: BookReviewItemUi, onImageClick: (String) -> Unit) {'
new = 'private fun ReviewReplyItem(reply: BookReviewItemUi, onImageClick: (String) -> Unit, onAudioClick: (String) -> Unit) {'
if old not in text: raise SystemExit('reply args missing')
text = text.replace(old, new, 1)
anchor = '''        reply.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->\n            AsyncImage(\n                model = imageUrl,\n                contentDescription = "回复图片",\n                contentScale = ContentScale.Fit,\n                modifier = Modifier\n                    .padding(start = 34.dp, top = 6.dp)\n                    .fillMaxWidth()\n                    .heightIn(max = 220.dp)\n                    .clip(RoundedCornerShape(10.dp))\n                    .clickable { onImageClick(imageUrl) },\n            )\n        }\n'''
addition = anchor + '''        reply.audioUrl?.takeIf { it.isNotBlank() }?.let { audioUrl ->\n            TextButton(onClick = { onAudioClick(audioUrl) }, modifier = Modifier.padding(start = 22.dp)) {\n                Icon(Icons.Outlined.PlayCircleOutline, contentDescription = null)\n                Text("播放语音", modifier = Modifier.padding(start = 6.dp))\n            }\n        }\n'''
if anchor not in text: raise SystemExit('reply image anchor missing')
ui.write_text(text.replace(anchor, addition, 1))

route = Path('app/src/main/java/io/legado/app/ui/book/info/BookInfoRouteScreen.kt')
text = route.read_text()
text = text.replace('import androidx.compose.runtime.getValue\n', 'import androidx.compose.runtime.getValue\nimport androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.remember\nimport androidx.compose.runtime.setValue\n')
text = text.replace('import io.legado.app.model.SourceCallBack\n', 'import io.legado.app.model.SourceCallBack\nimport io.legado.app.help.exoplayer.ExoPlayerHelper\nimport io.legado.app.model.analyzeRule.AnalyzeUrl\nimport io.legado.app.model.analyzeRule.AnalyzeUrl.Companion.getMediaItem\n')
anchor = '''    val showMangaUi by rememberUpdatedState(uiState.showMangaUi)\n'''
addition = anchor + '''    val reviewAudioPlayer = remember(activity) { ExoPlayerHelper.createHttpExoPlayer(activity) }\n    var currentReviewAudioUrl by remember { mutableStateOf<String?>(null) }\n\n    DisposableEffect(reviewAudioPlayer) {\n        onDispose {\n            runCatching {\n                reviewAudioPlayer.stop()\n                reviewAudioPlayer.clearMediaItems()\n                reviewAudioPlayer.release()\n            }\n        }\n    }\n'''
if anchor not in text: raise SystemExit('route player anchor missing')
text = text.replace(anchor, addition, 1)
old = '''                is BookInfoEffect.OpenFile -> activity.openFileUri(effect.uri, effect.mimeType)\n                is BookInfoEffect.RunSourceCallback -> {'''
new = '''                is BookInfoEffect.OpenFile -> activity.openFileUri(effect.uri, effect.mimeType)\n                is BookInfoEffect.PlayBookReviewAudio -> {\n                    runCatching {\n                        if (currentReviewAudioUrl == effect.audioUrl) {\n                            if (reviewAudioPlayer.isPlaying) reviewAudioPlayer.pause() else reviewAudioPlayer.play()\n                        } else {\n                            currentReviewAudioUrl = effect.audioUrl\n                            val mediaItem = AnalyzeUrl(effect.audioUrl, source = effect.source).getMediaItem()\n                            reviewAudioPlayer.setMediaItem(mediaItem, true)\n                            reviewAudioPlayer.prepare()\n                            reviewAudioPlayer.playWhenReady = true\n                        }\n                    }.onFailure { error ->\n                        currentReviewAudioUrl = null\n                        context.toastOnUi(error.localizedMessage ?: "语音播放失败")\n                    }\n                }\n                is BookInfoEffect.RunSourceCallback -> {'''
if old not in text: raise SystemExit('route effect anchor missing')
route.write_text(text.replace(old, new, 1))
print('upgraded source-aware book review audio playback')
