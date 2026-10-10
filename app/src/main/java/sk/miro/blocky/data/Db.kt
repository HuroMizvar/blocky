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
    val source: String // "EKASA" | "MANUAL" | "DEMO"
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
    val categoryManual: Boolean = false
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
    val issuedAt: Long
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

    @Query("SELECT * FROM receipts ORDER BY issuedAt DESC")
    abstract fun receipts(): Flow<List<ReceiptEntity>>

    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :id")
    abstract fun receiptWithItems(id: Long): Flow<ReceiptWithItems?>

    @Query("DELETE FROM receipts WHERE id = :id")
    abstract suspend fun deleteReceipt(id: Long)

    @Query("DELETE FROM receipts WHERE source = :source")
    abstract suspend fun deleteBySource(source: String): Int

    @Query("SELECT COUNT(*) FROM receipts WHERE source = :source")
    abstract fun countBySource(source: String): Flow<Int>

    @Query(
        """SELECT i.id AS itemId, i.receiptDbId, i.name, i.normName, i.quantity, i.priceCents,
                  i.category, r.merchantName, r.issuedAt
           FROM items i JOIN receipts r ON r.id = i.receiptDbId
           ORDER BY r.issuedAt"""
    )
    abstract fun allItemRows(): Flow<List<ItemRow>>

    @Query("UPDATE items SET category = :category, categoryManual = 1 WHERE id = :itemId")
    abstract suspend fun setItemCategory(itemId: Long, category: String)

    @Query("UPDATE items SET category = :category, categoryManual = 1 WHERE normName = :normName")
    abstract suspend fun setCategoryForName(normName: String, category: String)

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
}

@Database(
    entities = [ReceiptEntity::class, ItemEntity::class, BudgetEntity::class, CategoryRuleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): BlockyDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "blocky.db")
                .build().also { instance = it }
        }
    }
}
