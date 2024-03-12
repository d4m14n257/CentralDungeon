import { useRef, useState } from 'react';

import CardContent from '@mui/material/CardContent';
import CardActions from '@mui/material/CardActions';
import Button from '@mui/material/Button';
import ButtonGroup from '@mui/material/ButtonGroup';

import CardContentTable from './CardContentTable';

import EditModalTable from './modals/EditModalTable';

/*
    - Preparacion (solo admin y master)
    - Abierto (todos la ven)
    - En proceso (Ya es privada para el publico)
        - En proceso abierto (sub proceso en caso de que el master requiera algo mas para su continuacion)
        - Pausado (En caso de que esta tenga un incidente pero por causas mayores se tuvo que detener)
    - Cancelado (Esta murio por varios factores, debe tener una razon de la misma)
    - Finalizado (Esta concluyo su ciclo de vida normal / Este proceso siempre es natural no puede ser forzado a menos que un
                    admin tenga mano en esto.) 
*/

export default function CardBodyTableMaster (props) {
    const { table } = props;
    const [ edit, setEdit ] = useState(false);

    const handleOpenEditModal = () => {
        setEdit(true);
    }

    const handleCloseEditModal = () => {
        setEdit(false);
    }

    return (
        <>
            <CardContent>
                <CardContentTable 
                    table={table}
                    haveSystem={true}
                />
            </CardContent>
            {edit && 
                <EditModalTable 
                    isOpen={edit}
                    handleCloseModal={handleCloseEditModal}
                />
            }
        </>
    );
}