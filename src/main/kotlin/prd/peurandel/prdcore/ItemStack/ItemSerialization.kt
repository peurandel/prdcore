package prd.peurandel.prdcore.ItemStack

import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.*


object ItemSerialization {
    fun serializeItemStack(itemStack: ItemStack?): String {
        try {
            val outputStream = ByteArrayOutputStream()
            val dataOutput = BukkitObjectOutputStream(outputStream)
            dataOutput.writeObject(itemStack)
            dataOutput.close()
            return Base64.getEncoder().encodeToString(outputStream.toByteArray())
        } catch (e: IOException) {
            throw RuntimeException("Unable to serialize ItemStack", e)
        }
    }

    fun deserializeItemStack(base64: String?): ItemStack {
        try {
            val inputStream = ByteArrayInputStream(Base64.getDecoder().decode(base64))
            val dataInput = BukkitObjectInputStream(inputStream)
            val itemStack = dataInput.readObject() as ItemStack
            dataInput.close()
            return itemStack
        } catch (e: IOException) {
            throw RuntimeException("Unable to deserialize ItemStack", e)
        } catch (e: ClassNotFoundException) {
            throw RuntimeException("Unable to deserialize ItemStack", e)
        }
    }
}
