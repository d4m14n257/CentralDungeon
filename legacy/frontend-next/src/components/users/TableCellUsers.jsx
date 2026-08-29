import Button from '@mui/material/Button';
import ButtonGroup from '@mui/material/ButtonGroup';
import ThumbUpAltIcon from '@mui/icons-material/ThumbUpAlt';
import ThumbDownAltIcon from '@mui/icons-material/ThumbDownAlt';
import DragHandleIcon from '@mui/icons-material/DragHandle';

export default function TableCellUsers (props) {
    const { index, row } = props;

    if(index === 'joined_table') {
        return (
            <ButtonGroup variant="outlined" aria-label="outlined button group">
                <Button size="small">{row.inscrited_table}</Button>
                <Button size="small">{row.active_table}</Button>
            </ButtonGroup>
        );
    }
    else if(index === 'master_table') {
        return(
            <ButtonGroup variant="outlined" aria-label="outlined button group">
                <Button size="small">{row.active_master_table}</Button>
                <Button size="small">{row.created_master_table}</Button>
            </ButtonGroup>
        );
    }
    else if(index === 'comments') {
        return(
            <ButtonGroup variant="outlined" aria-label="outlined button group">
                <Button size="small" endIcon={<ThumbUpAltIcon/>}>{row.positive_comments}</Button>
                <Button size="small" endIcon={<DragHandleIcon />}>{row.neutral_comments}</Button>
                <Button size="small" endIcon={<ThumbDownAltIcon/>}>{row.negative_comments}</Button>
            </ButtonGroup>
        );
    }

}