package io.github.lemonjelly76.babymilestones.server.store

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

@Repository
class FamilyStore(private val jdbc: JdbcClient) {

    fun createFamily(family: Family) {
        jdbc.sql("insert into family (id, name, region) values (:id, :name, :region)")
            .param("id", family.id).param("name", family.name).param("region", family.region).update()
    }

    fun family(id: String): Family? = jdbc.sql("select * from family where id = :id").param("id", id)
        .query { rs, _ -> Family(rs.getString("id"), rs.getString("name"), rs.getString("region")) }.optional().orElse(null)

    fun updateFamily(family: Family) {
        jdbc.sql("update family set name = :name, region = :region where id = :id")
            .param("id", family.id).param("name", family.name).param("region", family.region).update()
    }

    fun deleteFamily(id: String) {
        jdbc.sql("delete from family where id = :id").param("id", id).update()
    }

    fun familyCount(): Int = jdbc.sql("select count(*) from family").query(Int::class.java).single()

    fun createCarer(carer: Carer) {
        jdbc.sql("insert into carer (id, family_id, name, email, role) values (:id, :familyId, :name, :email, :role)")
            .param("id", carer.id).param("familyId", carer.familyId).param("name", carer.name)
            .param("email", carer.email).param("role", carer.role).update()
    }

    fun carers(familyId: String): List<Carer> =
        jdbc.sql("select * from carer where family_id = :familyId order by created_at").param("familyId", familyId)
            .query { rs, _ -> carer(rs) }.list()

    fun carer(id: String): Carer? = jdbc.sql("select * from carer where id = :id").param("id", id)
        .query { rs, _ -> carer(rs) }.optional().orElse(null)

    fun storeToken(hash: String, carerId: String, label: String, expiresAt: Instant?) {
        jdbc.sql("insert into access_token (token_hash, carer_id, label, expires_at) values (:hash, :carerId, :label, :expiresAt)")
            .param("hash", hash).param("carerId", carerId).param("label", label)
            .param("expiresAt", expiresAt?.let(Timestamp::from)).update()
    }

    /** The carer a live token belongs to, marking it used. */
    fun carerForToken(hash: String, now: Instant): Carer? {
        val carer = jdbc.sql(
            """
            select c.* from access_token t join carer c on c.id = t.carer_id
            where t.token_hash = :hash and (t.expires_at is null or t.expires_at > :now)
            """,
        ).param("hash", hash).param("now", Timestamp.from(now)).query { rs, _ -> carer(rs) }.optional().orElse(null)
        if (carer != null) {
            jdbc.sql("update access_token set last_used_at = :now where token_hash = :hash")
                .param("now", Timestamp.from(now)).param("hash", hash).update()
        }
        return carer
    }

    fun revokeTokens(carerId: String, label: String) {
        jdbc.sql("delete from access_token where carer_id = :carerId and label = :label")
            .param("carerId", carerId).param("label", label).update()
    }

    fun storeInvite(codeHash: String, familyId: String, createdBy: String, expiresAt: Instant) {
        jdbc.sql("insert into invite (code_hash, family_id, created_by, expires_at) values (:hash, :familyId, :by, :expiresAt)")
            .param("hash", codeHash).param("familyId", familyId).param("by", createdBy)
            .param("expiresAt", Timestamp.from(expiresAt)).update()
    }

    /** Marks an unused, unexpired invite as used and returns its family, or null if it can't be used. */
    fun redeemInvite(codeHash: String, now: Instant): String? =
        jdbc.sql(
            """
            update invite set used_at = :now
            where code_hash = :hash and used_at is null and expires_at > :now
            returning family_id
            """,
        ).param("hash", codeHash).param("now", Timestamp.from(now)).query(String::class.java).optional().orElse(null)

    fun storePhoto(photo: Photo, bytes: ByteArray) {
        jdbc.sql("insert into photo (id, family_id, content_type, bytes) values (:id, :familyId, :contentType, :bytes)")
            .param("id", photo.id).param("familyId", photo.familyId).param("contentType", photo.contentType)
            .param("bytes", bytes).update()
    }

    fun photo(familyId: String, id: String): Pair<String, ByteArray>? =
        jdbc.sql("select content_type, bytes from photo where id = :id and family_id = :familyId")
            .param("id", id).param("familyId", familyId)
            .query { rs, _ -> rs.getString("content_type") to rs.getBytes("bytes") }.optional().orElse(null)

    private fun carer(rs: ResultSet) = Carer(
        rs.getString("id"), rs.getString("family_id"), rs.getString("name"), rs.getString("email"), rs.getString("role"),
    )
}
