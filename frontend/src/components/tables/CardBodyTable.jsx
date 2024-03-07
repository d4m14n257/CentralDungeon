import { useEffect, useRef, useState } from 'react';

import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import CardContent from '@mui/material/CardContent';
import CardActions from '@mui/material/CardActions';
import Button from '@mui/material/Button';
import ButtonGroup from '@mui/material/ButtonGroup';

import RequerimentsModalTable from './RequerimentsModalTable'; 

import { card } from '@/styles/tables/card';
import RequestModalTable from './RequestModalTable';

const Span = ({title}) => {
    return (
        <span style={{fontWeight: 'bold'}}>
            {title}
        </span>
    );
}

export default function CardBodyTable (props) {
    const { details } = props;

    const [openRequerimentsModal, setOpenRequerimentsModal] = useState(false);
    const [openRequestModal, setOpenRequestModal] = useState(false);

    const date = useRef(null);

    const handleOpenRequerimentsModal = () => {
        setOpenRequerimentsModal(true);
    }

    const handleCloseRequerimentsModal = () => {
        setOpenRequerimentsModal(false);
    }

    const handleOpenRequestModal = () => {
        setOpenRequestModal(true);
    }

    const handleCloseRequestModal = () => {
        setOpenRequestModal(false);
    }

    useEffect(() => {
        if(details) {
            const startDate = new Date(details.startdate);
            const options = { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric', hour: 'numeric', minute: 'numeric' };
            date.current = startDate.toLocaleDateString('es-ES', options);
        }
    }, [details])

    return (
        <>
            <CardContent>
                <Typography>
                    <Span title={'Descripción: '} />
                    {details.descripction}
                </Typography>
                <Typography>
                    <Span title={'Detalles: '} />
                    {details.allowsmaster}
                </Typography>
                <Typography>
                    <Span title={'Tags: '} /> 
                    {details.tag ? 
                        details.tag.join(', ')         
                        : "No hay tags asignados"
                    }
                </Typography>
                <Typography>
                    <Span title={'Fecha de inicio: '} />
                    {date.current} UTC-06:00
                </Typography>
                <Typography>
                    <Span title={'Zona horaria de la mesa: '}/>
                    {details.timezone}
                </Typography>
                <Typography>
                    <Span title={'Horario: '} />
                    {details.schedule.join(', ')}
                </Typography>
                <Typography>
                    <Span title={'Estado: '} />
                    {details.status}
                </Typography>
            </CardContent>
            <CardActions sx={card.cardActions}>
                <ButtonGroup>
                    <Button onClick={handleOpenRequerimentsModal}>Requisitos</Button>
                    <Button onClick={handleOpenRequestModal}>Solicitar</Button>
                </ButtonGroup>
            </CardActions>
            <RequerimentsModalTable 
                isOpen={openRequerimentsModal}
                handleCloseModal={handleCloseRequerimentsModal}
                handleOpenRequestModal={handleOpenRequestModal}
                requeriments={details.requeriments}
            />
            <RequestModalTable 
                isOpen={openRequestModal}
                handleCloseModal={handleCloseRequestModal}
            />
        </>
    );
}