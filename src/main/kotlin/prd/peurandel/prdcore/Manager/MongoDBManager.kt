package prd.peurandel.prdcore.Manager

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoDatabase
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import org.bson.Document

class MongoDBManager(private val connectionString: String) {
    private var client: MongoClient? = null
    private var database: MongoDatabase? = null
    private var collection: MongoCollection<Document>? = null

    init {
        connect()
    }

    fun connectToDataBase(name: String): MongoDatabase {
        //mongoClient = MongoClients.create(settings);
        return client!!.getDatabase(name)
    }
    private fun connect() {
        client = MongoClients.create(connectionString)
        database = client?.getDatabase("server")
    }

    fun setCollection(collectionName: String) {
        collection = database!!.getCollection(collectionName)
    }

    fun getCollection(name: String): MongoCollection<Document>? {
        collection = database?.getCollection(name)
        return collection
    }
    fun getDocument(keyPath: String, value: Any): Document {
        val keys = keyPath.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var filter = Filters.eq(keys[keys.size - 1], value)
        for (i in keys.size - 2 downTo 0) {
            filter = Filters.elemMatch(keys[i], filter)
        }
        return collection!!.find(filter).first()
    }

    fun updateDocument(keyPath: String, value: Any, updateKeyPath: String?, updateValue: Any) {
        val keys = keyPath.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var filter = Filters.eq(keys[keys.size - 1], value)
        for (i in keys.size - 2 downTo 0) {
            filter = Filters.elemMatch(keys[i], filter)
        }
        collection!!.updateOne(filter, Updates.set(updateKeyPath, updateValue))
    }

    fun close() {
        client?.close()
    }
}