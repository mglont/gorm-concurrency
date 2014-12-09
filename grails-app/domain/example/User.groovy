package example

/**
 * This class is based on the one the Spring Security Core plugin generates.
 * See http://burtbeckwith.github.com/grails-spring-security-core/
 */
class User implements Serializable {
    private static final long serialVersionUID = 1L

    String username
    String password

    static constraints = {
        username(blank: false, unique: true)
        password(blank: false)
    }

    static mapping = {
        table '`user`'
        password(column: '`password`')
    }

    String toString() {
        "User $id: $username - $password"
    }

    boolean equals(def other) {
        if (!(other instanceof User)) {
            return false
        }
        this.id == other.id && this.username == other.username &&
                this.password == other.password
    }
}
