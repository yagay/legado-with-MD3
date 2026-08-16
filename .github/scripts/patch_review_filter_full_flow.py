from pathlib import Path

# 1. DAO: expose the complete BookSource table as a Room Flow so ruleReview changes
#    participate in the same invalidation/update chain as the source manager list.
p = Path('app/src/main/java/io/legado/app/data/dao/BookSourceDao.kt')
s = p.read_text()
old = '''    @Query("select * from book_sources_part order by customOrder asc")
    fun flowAll(): Flow<List<BookSourcePart>>
'''
new = '''    @Query("select * from book_sources_part order by customOrder asc")
    fun flowAll(): Flow<List<BookSourcePart>>

    @Query("select * from book_sources order by customOrder asc")
    fun flowAllSources(): Flow<List<BookSource>>
'''
if old not in s:
    raise SystemExit('BookSourceDao flowAll target not found')
s = s.replace(old, new, 1)
p.write_text(s)

# 2. Repository: expose that full-source Flow without a second suspend query.
p = Path('app/src/main/java/io/legado/app/data/repository/BookSourceRepository.kt')
s = p.read_text()
old = '''    fun flowAll(): Flow<List<BookSourcePart>> {
        return bookSourceDao.flowAll()
    }
'''
new = '''    fun flowAll(): Flow<List<BookSourcePart>> {
        return bookSourceDao.flowAll()
    }

    fun flowAllSources(): Flow<List<BookSource>> {
        return bookSourceDao.flowAllSources()
    }
'''
if old not in s:
    raise SystemExit('BookSourceRepository flowAll target not found')
s = s.replace(old, new, 1)
p.write_text(s)

# 3. ViewModel: derive review capabilities directly from the full BookSource Flow.
#    Do not stateIn with an empty initial capability set; sourceFilter itself is
#    collected by the UI state chain and will receive the real Room emission.
p = Path('app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceViewModel.kt')
s = p.read_text()
old = '''    private val reviewCapabilityFilters = repository.flowAll()
        .map {
            val sources = repository.getAll()
            ReviewCapabilityFilters(
                bookReviewUrls = sources.asSequence()
                    .filter { it.hasBookReviewCapability() }
                    .map { it.bookSourceUrl }
                    .toSet(),
                paragraphReviewUrls = sources.asSequence()
                    .filter { it.hasParagraphReviewCapability() }
                    .map { it.bookSourceUrl }
                    .toSet(),
                otherCommentUrls = sources.asSequence()
                    .filter { it.hasOtherCommentCapability() }
                    .map { it.bookSourceUrl }
                    .toSet(),
            )
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ReviewCapabilityFilters(),
        )
'''
new = '''    private val reviewCapabilityFilters = repository.flowAllSources()
        .map { sources ->
            ReviewCapabilityFilters(
                bookReviewUrls = sources.asSequence()
                    .filter { it.hasBookReviewCapability() }
                    .map { it.bookSourceUrl }
                    .toSet(),
                paragraphReviewUrls = sources.asSequence()
                    .filter { it.hasParagraphReviewCapability() }
                    .map { it.bookSourceUrl }
                    .toSet(),
                otherCommentUrls = sources.asSequence()
                    .filter { it.hasOtherCommentCapability() }
                    .map { it.bookSourceUrl }
                    .toSet(),
            )
        }
        .flowOn(Dispatchers.IO)
'''
if old not in s:
    raise SystemExit('BookSourceViewModel reviewCapabilityFilters target not found')
s = s.replace(old, new, 1)
p.write_text(s)
