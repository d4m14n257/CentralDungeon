export const setter = async ({
    id = null, 
    others = null,
    data, 
    url
    }) => {
    try {
        let complete_url = `${process.env.NEXT_PUBLIC_SERVER}/${url}`;

        if(id)
            complete_url += `/${id}`;

        if(others)
            complete_url += `/${others}`

        const option = {
            method: 'POST',
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify(data)
        }
        const response = await fetch(complete_url, option)
        .catch((err) => {
            throw {...err.cause, status: 500}
        })

        const status = response.status
        const body = await response.json()

        return await { status, ...body }

    } catch (err) {
        return err
    }
}  