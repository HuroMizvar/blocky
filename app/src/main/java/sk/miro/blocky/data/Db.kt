package sk.miro.blocky.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Bloček. Sumy sú v centoch, aby nevznikali chyby zaokrúhľovania. */
@Entity(
    tableName = "receipts",
    indices = [Index(value = ["receiptId"], unique = true), Index("issuedAt")]
)
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** eKasa ID dokladu (O-...) alebo "MANUAL-<čas>" pre ručne zadaný. */
    val receiptId: String,
    val merchantName: String,
    val merchantIco: String?,
    val storeAddress: String?,
    /** epoch millis */
    val issuedAt: Long,
    val totalCents: Long,
    val rawJson: String?,
    val source: String, // "EKASA" | "MANUAL" | "DEMO"
    /** kľúč prevádzky (IČO alebo názov) – pozri MerchantResolver.key */
    val merchantKey: String? = null,
    /** MerchantType.name */
    val merchantType: String? = null,
    /** ID dokumentu na serveri domácnosti (null = ešte nesynchronizovaný) */
    val syncId: String? = null,
    /** meno člena domácnosti, ktorý bloček nahral */
    val addedBy: String? = null,
    val addedByUid: String? = null,
    /** treba odoslať na server (nový alebo upravený bloček) */
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
    /** čas poslednej úpravy (ms) – pri konflikte vyhráva novšia */
    @ColumnInfo(defaultValue = "0") val editedAt: Long = 0
)

/** Opravená položka – príklad, z ktorého sa vstavaná AI učí (normalizovaný názov → kategória). */
@Entity(tableName = "ai_examples")
data class AiExampleEntity(@PrimaryKey val normName: String, val category: String)

/** Zmazaný bloček, ktorého zmazanie treba ešte poslať na server. */
@Entity(tableName = "sync_tombstones")
data class SyncTombstoneEntity(@PrimaryKey val docId: String)

/** Zmenené nastavenie (pravidlo, typ prevádzky, rozpočet, profil) čakajúce na odoslanie. */
@Entity(tableName = "sync_outbox")
data class SyncOutboxEntity(
    @PrimaryKey val kindKey: String,
    val kind: String,
    val k: String,
    val value: String?,
    val at: Long
)

/**
 * Prevádzka. [type] = MerchantType.name. [userSet] = typ potvrdil používateľ
 * (odpoveď na otázku „čo je to za prevádzku?“) – má prednosť pred automatikou.
 */
@Entity(tableName = "merchants")
data class MerchantEntity(
    @PrimaryKey val mkey: String,
    val name: String,
    val type: String,
    val userSet: Boolean
)

@Entity(
    tableName = "items",
    foreignKeys = [ForeignKey(
        entity = ReceiptEntity::class,
        parentColumns = ["id"],
        childColumns = ["receiptDbId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("receiptDbId"), Index("normName"), Index("category")]
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val receiptDbId: Long,
    val name: String,
    /** názov bez diakritiky, malými písmenami – na porovnávanie cien */
    val normName: String,
    val quantity: Double,
    /** celková cena riadku (môže byť záporná pri zľave / vrátení) */
    val priceCents: Long,
    val vatRate: Double?,
    val itemType: String?,
    val category: String,
    /** true, ak kategóriu nastavil používateľ ručne */
    val categoryManual: Boolean = false,
    /** zľava priradená k tejto položke (kladné číslo, centy); priceCents je už po zľave */
    @ColumnInfo(defaultValue = "0") val discountCents: Long = 0,
    /** prečiarknutá položka – nezapočítava sa do výdavkov (vrátenie, reklamácia, omyl) */
    @ColumnInfo(defaultValue = "0") val excluded: Boolean = false,
    /** pôvodné hodnoty z bločku, ak používateľ zmenil množstvo (null = nezmenené) */
    val origQuantity: Double? = null,
    val origPriceCents: Long? = null,
    val origDiscountCents: Long? = null,
    /** kto určil kategóriu: null = pravidlá, "ai" = vstavaná AI, "nano" = AI telefónu, "nano-none" = AI telefónu nevedela */
    val catSource: String? = null
) {
    /** kategóriu navrhla umelá inteligencia (a používateľ ju zatiaľ neopravil) */
    val byAi: Boolean get() = !categoryManual && (catSource == "ai" || catSource == "nano")
    /** položka bola ručne upravená (prečiarknutá alebo zmenené množstvo) */
    val edited: Boolean get() = excluded || origQuantity != null
}

/** Bloček v zozname + súčet započítaných položiek (bez prečiarknutých). */
data class ReceiptListRow(
    @Embedded val receipt: ReceiptEntity,
    val countedCents: Long?,
    val excludedCount: Int,
    /** počet ručne upravených položiek (prečiarknuté alebo zmenené množstvo) */
    val editedCount: Int = 0,
    val itemCount: Int = 0,
    /** kategória najdrahšej položky (ikona ručne zadaného bločku) */
    val mainCategory: String? = null
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val category: String,
    val monthlyCents: Long
)

/** Ručné preradenie: názov položky → kategória. Platí pre budúce bločky. */
@Entity(tableName = "category_rules")
data class CategoryRuleEntity(
    @PrimaryKey val normName: String,
    val category: String
)

/**
 * Fronta naskenovaných QR kódov. Sken sa uloží okamžite (aplikáciu možno hneď zavrieť),
 * položky z eKasa sa načítajú na pozadí (FetchWorker) – aj neskôr, keď je signál.
 * status: PENDING | DONE | DUP (už existujúci bloček) | FAILED
 */
@Entity(tableName = "pending_scans")
data class PendingScanEntity(
    @PrimaryKey val raw: String,
    val createdAt: Long,
    val status: String,
    val attempts: Int,
    val message: String?,
    val receiptDbId: Long?
)

/** Riadok položky spojený s údajmi z bločku – základ všetkých analýz. */
data class ItemRow(
    val itemId: Long,
    val receiptDbId: Long,
    val name: String,
    val normName: String,
    val quantity: Double,
    val priceCents: Long,
    val category: String,
    val merchantName: String,
    val issuedAt: Long,
    val merchantType: String? = null
)

data class ReceiptWithItems(
    @Embedded val receipt: ReceiptEntity,
    @Relation(parentColumn = "id", entityColumn = "receiptDbId")
    val items: List<ItemEntity>
)

@Dao
abstract class BlockyDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertReceipt(r: ReceiptEntity): Long

    @Insert
    abstract suspend fun insertItems(items: List<ItemEntity>)

    @Transaction
    open suspend fun insertReceiptWithItems(r: ReceiptEntity, items: List<ItemEntity>): Long {
        val id = insertReceipt(r)
        insertItems(items.map { it.copy(receiptDbId = id) })
        return id
    }

    @Query("SELECT COUNT(*) FROM receipts WHERE receiptId = :receiptId")
    abstract suspend fun countByReceiptId(receiptId: String): Int

    @Query("SELECT * FROM receipts WHERE receiptId = :receiptId LIMIT 1")
    abstract suspend fun receiptByReceiptId(receiptId: String): ReceiptEntity?

    @Query("SELECT * FROM receipts WHERE id = :id")
    abstract suspend fun receiptById(id: Long): ReceiptEntity?

    @Query("SELECT * FROM receipts WHERE id = :id")
    abstract fun observeReceipt(id: Long): Flow<ReceiptEntity?>

    @Transaction
    @Query("SELECT * FROM receipts")
    abstract suspend fun allReceiptsWithItems(): List<ReceiptWithItems>

    @Transaction
    @Query("SELECT * FROM receipts WHERE merchantKey = :key")
    abstract suspend fun receiptsWithItemsByMerchant(key: String): List<ReceiptWithItems>

    @Query("UPDATE receipts SET merchantKey = :key, merchantType = :type WHERE id = :id")
    abstract suspend fun setReceiptMerchant(id: Long, key: String?, type: String?)

    @Query("UPDATE items SET category = :category, catSource = :source WHERE id = :id")
    abstract suspend fun setItemCategoryAuto(id: Long, category: String, source: String?)

    // ---- prevádzky ----
    @Query("SELECT * FROM merchants WHERE mkey = :key")
    abstract suspend fun merchant(key: String): MerchantEntity?

    @Upsert
    abstract suspend fun upsertMerchant(m: MerchantEntity)

    /** Prevádzky, pri ktorých sa treba opýtať, čo sú zač. */
    @Query("""SELECT * FROM merchants WHERE type = 'UNKNOWN' AND userSet = 0
              AND mkey IN (SELECT merchantKey FROM receipts WHERE source != 'MANUAL') ORDER BY name""")
    abstract fun unknownMerchants(): Flow<List<MerchantEntity>>

    // ---- zmazanie všetkého (GDPR – právo na výmaz) ----
    @Query("DELETE FROM receipts") abstract suspend fun clearReceipts()
    @Query("DELETE FROM budgets") abstract suspend fun clearBudgets()
    @Query("DELETE FROM category_rules") abstract suspend fun clearRules()
    @Query("DELETE FROM pending_scans") abstract suspend fun clearPending()
    @Query("DELETE FROM merchants") abstract suspend fun clearMerchants()

    @Transaction
    open suspend fun clearAll() {
        clearReceipts(); clearBudgets(); clearRules(); clearPending(); clearMerchants(); clearTombstones(); clearOutbox(); clearAiExamples()
    }

    @Query("SELECT * FROM receipts ORDER BY issuedAt DESC")
    abstract fun receipts(): Flow<List<ReceiptEntity>>

    @Query("""SELECT r.*,
                (SELECT SUM(i.priceCents) FROM items i WHERE i.receiptDbId = r.id AND i.excluded = 0) AS countedCents,
                (SELECT COUNT(*) FROM items i WHERE i.receiptDbId = r.id AND i.excluded = 1) AS excludedCount,
                (SELECT COUNT(*) FROM items i WHERE i.receiptDbId = r.id
                    AND (i.excluded = 1 OR i.origQuantity IS NOT NULL)) AS editedCount,
                (SELECT COUNT(*) FROM items i WHERE i.receiptDbId = r.id AND i.priceCents > 0) AS itemCount,
                (SELECT i.category FROM items i WHERE i.receiptDbId = r.id ORDER BY i.priceCents DESC LIMIT 1) AS mainCategory
              FROM receipts r ORDER BY r.issuedAt DESC""")
    abstract fun receiptList(): Flow<List<ReceiptListRow>>

    @Query("UPDATE items SET excluded = :excluded WHERE id = :id")
    abstract suspend fun setExcluded(id: Long, excluded: Boolean)

    @Query("UPDATE items SET priceCents = :price, discountCents = :discount WHERE id = :id")
    abstract suspend fun setItemPrice(id: Long, price: Long, discount: Long)

    @Query("DELETE FROM items WHERE id = :id")
    abstract suspend fun deleteItem(id: Long)

    @Query("SELECT * FROM items WHERE id = :id")
    abstract suspend fun itemById(id: Long): ItemEntity?

    @Query("""UPDATE items SET quantity = :qty, priceCents = :price, discountCents = :discount,
              origQuantity = :origQty, origPriceCents = :origPrice, origDiscountCents = :origDiscount WHERE id = :id""")
    abstract suspend fun setItemQuantity(id: Long, qty: Double, price: Long, discount: Long,
                                         origQty: Double?, origPrice: Long?, origDiscount: Long?)

    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :id")
    abstract fun receiptWithItems(id: Long): Flow<ReceiptWithItems?>

    @Query("DELETE FROM receipts WHERE id = :id")
    abstract suspend fun deleteReceipt(id: Long)

    // ---- fronta skenov ----
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertPending(p: PendingScanEntity): Long

    @Update
    abstract suspend fun updatePending(p: PendingScanEntity)

    @Query("SELECT * FROM pending_scans WHERE raw = :raw")
    abstract suspend fun pending(raw: String): PendingScanEntity?

    @Query("SELECT * FROM pending_scans WHERE raw = :raw")
    abstract fun observePending(raw: String): Flow<PendingScanEntity?>

    @Query("SELECT * FROM pending_scans WHERE status = 'PENDING' ORDER BY createdAt")
    abstract suspend fun pendingToProcess(): List<PendingScanEntity>

    @Query("SELECT * FROM pending_scans WHERE status IN ('PENDING', 'FAILED') ORDER BY createdAt DESC")
    abstract fun openPending(): Flow<List<PendingScanEntity>>

    @Query("DELETE FROM pending_scans WHERE raw = :raw")
    abstract suspend fun deletePending(raw: String)

    @Query("DELETE FROM pending_scans WHERE status IN ('DONE', 'DUP') AND createdAt < :before")
    abstract suspend fun cleanupDone(before: Long)

    @Query("DELETE FROM receipts WHERE source = :source")
    abstract suspend fun deleteBySource(source: String): Int

    @Query("SELECT COUNT(*) FROM receipts WHERE source = :source")
    abstract fun countBySource(source: String): Flow<Int>

    @Query(
        """SELECT i.id AS itemId, i.receiptDbId, i.name, i.normName, i.quantity, i.priceCents,
                  i.category, r.merchantName, r.issuedAt, r.merchantType
           FROM items i JOIN receipts r ON r.id = i.receiptDbId
           WHERE i.excluded = 0
           ORDER BY r.issuedAt"""
    )
    abstract fun allItemRows(): Flow<List<ItemRow>>

    @Query("UPDATE items SET category = :category, categoryManual = 1, catSource = NULL WHERE id = :itemId")
    abstract suspend fun setItemCategory(itemId: Long, category: String)

    @Query("UPDATE items SET category = :category, categoryManual = 1, catSource = NULL WHERE normName = :normName")
    abstract suspend fun setCategoryForName(normName: String, category: String)

    // ---- vstavaná AI ----
    @Upsert abstract suspend fun upsertAiExample(e: AiExampleEntity)
    @Query("SELECT * FROM ai_examples") abstract suspend fun aiExamples(): List<AiExampleEntity>
    @Query("DELETE FROM ai_examples") abstract suspend fun clearAiExamples()

    /** Položky pre AI telefónu: nezaradené alebo zaradené vstavanou AI, bez ručnej opravy. */
    @Query("""SELECT * FROM items WHERE categoryManual = 0 AND priceCents > 0
              AND (catSource = 'ai' OR (category = 'INE' AND catSource IS NULL)) LIMIT :limit""")
    abstract suspend fun itemsForPhoneAi(limit: Int): List<ItemEntity>

    @Upsert
    abstract suspend fun upsertRule(rule: CategoryRuleEntity)

    @Query("SELECT * FROM category_rules")
    abstract suspend fun rules(): List<CategoryRuleEntity>

    @Query("SELECT * FROM budgets")
    abstract fun budgets(): Flow<List<BudgetEntity>>

    @Upsert
    abstract suspend fun upsertBudget(b: BudgetEntity)

    @Query("DELETE FROM budgets WHERE category = :category")
    abstract suspend fun deleteBudget(category: String)

    @Query("SELECT * FROM budgets")
    abstract suspend fun budgetList(): List<BudgetEntity>

    @Query("DELETE FROM category_rules WHERE normName = :normName")
    abstract suspend fun deleteRule(normName: String)

    @Query("SELECT * FROM merchants WHERE userSet = 1")
    abstract suspend fun userMerchants(): List<MerchantEntity>

    // ---- synchronizácia domácnosti ----
    @Query("UPDATE receipts SET dirty = 1, editedAt = :at WHERE id = :id")
    abstract suspend fun markDirty(id: Long, at: Long)

    @Query("UPDATE receipts SET dirty = 1, editedAt = :at WHERE id = (SELECT receiptDbId FROM items WHERE id = :itemId)")
    abstract suspend fun markDirtyByItem(itemId: Long, at: Long)

    @Query("UPDATE receipts SET dirty = 1, editedAt = :at WHERE id IN (SELECT receiptDbId FROM items WHERE normName = :normName)")
    abstract suspend fun markDirtyByName(normName: String, at: Long)

    @Query("UPDATE receipts SET dirty = 1, editedAt = :at WHERE merchantKey = :key")
    abstract suspend fun markDirtyByMerchant(key: String, at: Long)

    /** Pri zapnutí synchronizácie: všetky vlastné bločky (okrem demo) poslať na server. */
    @Query("UPDATE receipts SET dirty = 1, addedBy = COALESCE(addedBy, :name), addedByUid = COALESCE(addedByUid, :uid) WHERE source != 'DEMO'")
    abstract suspend fun markAllDirty(name: String, uid: String)

    @Transaction
    @Query("SELECT * FROM receipts WHERE dirty = 1 AND source != 'DEMO'")
    abstract suspend fun dirtyReceipts(): List<ReceiptWithItems>

    @Query("UPDATE receipts SET dirty = 0, syncId = :syncId, addedBy = :who, addedByUid = :uid WHERE id = :id AND editedAt = :editedAt")
    abstract suspend fun markClean(id: Long, syncId: String, who: String?, uid: String?, editedAt: Long)

    @Query("SELECT * FROM receipts WHERE syncId = :syncId LIMIT 1")
    abstract suspend fun receiptBySyncId(syncId: String): ReceiptEntity?

    @Query("UPDATE items SET category = 'INE', categoryManual = 0 WHERE category = :category")
    abstract suspend fun clearCategory(category: String)

    @Query("DELETE FROM category_rules WHERE category = :category")
    abstract suspend fun deleteRulesFor(category: String)

    @Query("UPDATE receipts SET addedBy = :name, dirty = 1, editedAt = :at WHERE addedByUid = :uid")
    abstract suspend fun renameMine(uid: String, name: String, at: Long)

    @Query("UPDATE receipts SET dirty = 1, editedAt = :at WHERE id IN (SELECT receiptDbId FROM items WHERE category = :category)")
    abstract suspend fun markDirtyByCategory(category: String, at: Long)

    // presun kategórie (zlúčenie vlastných kategórií pri vstupe do domácnosti)
    @Query("UPDATE items SET category = :to WHERE category = :from")
    abstract suspend fun moveItemsCategory(from: String, to: String)
    @Query("UPDATE category_rules SET category = :to WHERE category = :from")
    abstract suspend fun moveRulesCategory(from: String, to: String)
    @Query("UPDATE ai_examples SET category = :to WHERE category = :from")
    abstract suspend fun moveAiCategory(from: String, to: String)
    @Query("UPDATE OR REPLACE budgets SET category = :to WHERE category = :from")
    abstract suspend fun moveBudgetCategory(from: String, to: String)

    @Transaction
    open suspend fun moveCategory(from: String, to: String) {
        moveItemsCategory(from, to); moveRulesCategory(from, to); moveAiCategory(from, to); moveBudgetCategory(from, to)
    }

    @Query("DELETE FROM ai_examples WHERE category = :category")
    abstract suspend fun deleteAiExamplesFor(category: String)

    @Query("DELETE FROM pending_scans WHERE receiptDbId = :receiptDbId")
    abstract suspend fun deletePendingFor(receiptDbId: Long)

    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :id")
    abstract suspend fun receiptWithItemsOnce(id: Long): ReceiptWithItems?

    @Query("DELETE FROM items WHERE receiptDbId = :receiptDbId")
    abstract suspend fun deleteItemsOf(receiptDbId: Long)

    @Update
    abstract suspend fun updateReceipt(r: ReceiptEntity)

    /** Nahradí bloček verziou zo servera (ponechá lokálne ID). */
    @Transaction
    open suspend fun replaceReceipt(r: ReceiptEntity, items: List<ItemEntity>) {
        updateReceipt(r)
        deleteItemsOf(r.id)
        insertItems(items.map { it.copy(id = 0, receiptDbId = r.id) })
    }

    @Query("UPDATE receipts SET syncId = NULL, dirty = 0")
    abstract suspend fun clearSyncIds()

    @Upsert abstract suspend fun addTombstone(t: SyncTombstoneEntity)
    @Query("SELECT * FROM sync_tombstones") abstract suspend fun tombstones(): List<SyncTombstoneEntity>
    @Query("DELETE FROM sync_tombstones WHERE docId = :docId") abstract suspend fun removeTombstone(docId: String)
    @Query("DELETE FROM sync_tombstones") abstract suspend fun clearTombstones()

    @Upsert abstract suspend fun addOutbox(o: SyncOutboxEntity)
    @Query("SELECT * FROM sync_outbox") abstract suspend fun outbox(): List<SyncOutboxEntity>
    @Query("DELETE FROM sync_outbox WHERE kindKey = :kindKey AND at = :at") abstract suspend fun removeOutbox(kindKey: String, at: Long)
    @Query("DELETE FROM sync_outbox") abstract suspend fun clearOutbox()
}

@Database(
    entities = [ReceiptEntity::class, ItemEntity::class, BudgetEntity::class, CategoryRuleEntity::class,
        PendingScanEntity::class, MerchantEntity::class, SyncTombstoneEntity::class, SyncOutboxEntity::class, AiExampleEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): BlockyDao

    companion object {
        /** v2: fronta skenov – existujúce bločky ostávajú zachované */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pending_scans` (`raw` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, `attempts` INTEGER NOT NULL, `message` TEXT, `receiptDbId` INTEGER, " +
                        "PRIMARY KEY(`raw`))"
                )
            }
        }

        /** v3: typ prevádzky pri bločku + tabuľka prevádzok */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `receipts` ADD COLUMN `merchantKey` TEXT")
                db.execSQL("ALTER TABLE `receipts` ADD COLUMN `merchantType` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `merchants` (`mkey` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, `userSet` INTEGER NOT NULL, PRIMARY KEY(`mkey`))"
                )
            }
        }

        /** v4: zľavy priradené k položkám + prečiarknuté (nezapočítané) položky */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `items` ADD COLUMN `discountCents` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `items` ADD COLUMN `excluded` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v5: zmena množstva položky – pôvodné hodnoty z bločku */
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `items` ADD COLUMN `origQuantity` REAL")
                db.execSQL("ALTER TABLE `items` ADD COLUMN `origPriceCents` INTEGER")
                db.execSQL("ALTER TABLE `items` ADD COLUMN `origDiscountCents` INTEGER")
            }
        }

        /** v6: synchronizácia domácnosti */
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `receipts` ADD COLUMN `syncId` TEXT")
                db.execSQL("ALTER TABLE `receipts` ADD COLUMN `addedBy` TEXT")
                db.execSQL("ALTER TABLE `receipts` ADD COLUMN `addedByUid` TEXT")
                db.execSQL("ALTER TABLE `receipts` ADD COLUMN `dirty` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `receipts` ADD COLUMN `editedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS `sync_tombstones` (`docId` TEXT NOT NULL, PRIMARY KEY(`docId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `sync_outbox` (`kindKey` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                    "`k` TEXT NOT NULL, `value` TEXT, `at` INTEGER NOT NULL, PRIMARY KEY(`kindKey`))")
            }
        }

        /** v7: vstavaná AI – zdroj kategórie a príklady z opráv */
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `items` ADD COLUMN `catSource` TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS `ai_examples` (`normName` TEXT NOT NULL, `category` TEXT NOT NULL, PRIMARY KEY(`normName`))")
            }
        }

        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: run {
                // obnova zo zálohy sa dokončí pred otvorením databázy
                Backup.applyPendingRestore(context.applicationContext)
                Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "blocky.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build().also { instance = it }
            }
        }
    }
}
