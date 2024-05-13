import { generateBoudary } from "@/helper/generateBoudary";

export const setter = async ({
    id = null, 
    others = null,
    data, 
    url, 
    file = false}) => {
    try {
        let complete_url = `${process.env.NEXT_PUBLIC_SERVER}/${url}`;

        if(id)
            complete_url += `/${id}`;

        if(others)
            complete_url += `/${others}`

        const option = {
            method: 'POST',
            headers: {
                "Content-Type": file ? `multipart/form-data; boundary=${generateBoudary()};` : "application/json",
                
            },
            body: file ? data : JSON.stringify(data)
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