from pathlib import Path
p = Path('app/src/main/java/io/legado/app/ui/widget/components/settingItem/SliderSettingItem.kt')
s = p.read_text(encoding='utf-8')
s = s.replace(
'''    var expanded by remember { mutableStateOf(false) }\n    var isInputMode by remember { mutableStateOf(false) }\n    var sliderValue by remember(value) { mutableFloatStateOf(value) }\n''',
'''    var expanded by remember { mutableStateOf(false) }\n    var isInputMode by remember { mutableStateOf(false) }\n    var sliderValue by remember { mutableFloatStateOf(value) }\n    var previewStartValue by remember { mutableFloatStateOf(value) }\n''')
s = s.replace(
'''        expanded = expanded,\n        onExpandChange = { expanded = it },\n''',
'''        expanded = expanded,\n        onExpandChange = { newExpanded ->\n            if (newExpanded && !expanded) {\n                previewStartValue = value\n                sliderValue = value\n            } else if (!newExpanded && expanded) {\n                sliderValue = previewStartValue\n                onValuePreviewChange?.invoke(previewStartValue)\n            }\n            expanded = newExpanded\n        },\n''')
s = s.replace(
'''                        onDismiss = {\n                            sliderValue = value\n                            onValuePreviewChange?.invoke(value)\n                            expanded = false\n                        },''',
'''                        onDismiss = {\n                            sliderValue = previewStartValue\n                            onValuePreviewChange?.invoke(previewStartValue)\n                            expanded = false\n                        },''')
p.write_text(s, encoding='utf-8')
