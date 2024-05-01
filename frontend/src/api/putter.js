export const putter = async (data, url) => {
    try {
        const option = {
            method: 'PUT',
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify(data)
        }
        const response = await fetch(`${process.env.NEXT_PUBLIC_SERVER}/${url}`, option)
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