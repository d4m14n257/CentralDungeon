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

export const TablesInfo = (tables) => {
    return {
        id: tables.id !== undefined ? tables.id : null,
        table_type: tables.table_type !== undefined ? tables.table_type : null,
        name: tables.name !== undefined ? tables.name : null,
        requeriments: tables.requeriments !== undefined ? tables.requeriments : null,
        description: tables.description !== undefined ? tables.description : null,
        permitted: tables.permitted !== undefined ? tables.permitted : null,
        startdate: tables.startdate !== undefined ? tables.startdate : null,
        timezone: tables.timezone !== undefined ? tables.timezone : null,
        status: tables.table_status !== undefined ? tables.table_status : null,
        duration: tables.duration !== undefined ? tables.duration : null,
        tags: tables.tags !== undefined ? tables.tags : null,
        systems: tables.systems !== undefined ? tables.systems : null,
        platforms: tables.platforms !== undefined ? tables.platforms : null,
        masters: tables.masters !== undefined ? tables.masters : null,
        schedule: tables.schedule !== undefined ? tables.schedule : null,
        files: tables.files !== undefined ? Files(tables.files) : null,
        players: tables.players !== undefined ? tables.players : null,
    }
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

export const Files = (files) => {
    return files.map((file) => ({
        id: file.id,
        name: file.name,
        type: file.mine,
        size: file.size
    }))
}