export const deleter = async ({
        id, 
        others = null,
        url,
        body = null
    }) => {
    try {
        let complete_url = `${process.env.NEXT_PUBLIC_SERVER}/${url}`;

        if(id)
            complete_url += `/${id}`;

        if(others)
            complete_url += `/${others}`;

        const option = {
            method: 'DELETE',
            headers: {
                "Content-Type": "application/json",
            },
            body: body && JSON.stringify(body)
        }
        const response = await fetch(complete_url, option)
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