export const Tables = (tables) => {
    return tables.map((table) => ({
        id: table.id !== undefined ? table.id : null,
        name: table.name !== undefined ? table.name : null,
        description: table.description !== undefined ? table.description : null,
        masters: table.masters !== undefined ? table.masters : null,
        players: table.players !== undefined ? table.players : null,
        tags: table.tags !== undefined ? table.tags : null,
        startdate: table.startdate !== undefined ? table.startdate : null,
        status: table.status !== undefined ? table.status : null,
        table_type: table.table_type !== undefined ? table.table_type : null
    }))
}

export const Request = (requests) => {
    return requests.map((request) => ({
        id: request.id !== undefined ? request.id : null,
        table_id: request.table_id !== undefined ? request.table_id : null,
        name: request.name !== undefined ? request.name : null,
        username: request.username !== undefined ? request.username : null,
        masters: request.masters !== undefined ? request.masters : null,
        status: request.status !== undefined ? request.status : null,
        startdate: request.startdate !== undefined ? request.startdate : null,
        karma: request.karma !== undefined ? request.karma : null,
        created: request.created_at !== undefined ? request.created_at : null,
    }))
}

export const Rejected = (rejected) => {
    return {
        description_rejected: rejected.description,
        rejected_date: rejected.rejected_date 
    }
}