export const deleter = async (id, url) => {
    try {
        const option = {
            method: 'DELETE',
            headers: {
                "Content-Type": "application/json",
            },
        }
        const response = await fetch(`${process.env.NEXT_PUBLIC_SERVER}/${url}/${id}`, option)
        .catch((err) => {
            throw {...err.cause, status: 500}
        })

        const status = response.status
        const message = await response.json()

        return { status, message }

    } catch (err) {
        return err
    }
}  