import { useEffect, useState } from "react";
import ListComponent from "../ListComponent";

const select = [
    {
        id: 1,
        name: 'Nombre de mesa 1',
        description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
    },
    {
        id: 2,
        name: 'Nombre de mesa 2',
        description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
    },
    {
        id: 3,
        name: 'Nombre de mesa 3',
        description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
    },
    {
        id: 4,
        name: 'Nombre de mesa 4',
        description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
    },
    {
        id: 5,
        name: 'Nombre de mesa 5',
        description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
    },
    {
        id: 6,
        name: 'Nombre de mesa 6',
        description: 'Esta es una descipcion para ver como se veria como un subtitulo y no se si limitarla por si llegan a ser demaciado larga xd'
    },
]

export default function TableSelect (props) {
    const { handleTableSelect, tableSelect } = props;
    const [tables, setTables] = useState(null);

    useEffect(() => {
        setTables(select);
    }, [])

    return (
        <ListComponent 
            tables={tables}
            title={tableSelect}
        />
    );
}