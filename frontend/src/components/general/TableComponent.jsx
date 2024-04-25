import { useState, useReducer, useMemo, useCallback } from 'react';

import { Paper, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, TableCellUsers, Toolbar, Typography,
         Box, Checkbox, Tooltip, IconButton } from '@mui/material';

import { alpha } from '@mui/material/styles';

import DeleteIcon from '@mui/icons-material/Delete';
import FilterListIcon from '@mui/icons-material/FilterList';
import CancelIcon from '@mui/icons-material/Cancel';

import { global } from '@/styles/global';

function reducer (state, action) {
    const { type, value } = action;

    switch (type) {
        case 'change-row-per-page': {
            const newValue = state.rowsPerPage + value;

            return {
                ...state,
                rowsPerPage: newValue,
            }
        }
    }
}

function stableSort(array, comparator) {
    const stabilizedThis = array.map((el, index) => [el, index]);

    stabilizedThis.sort((a, b) => {
        const order = comparator(a[0], b[0]);
        if (order !== 0) {
            return order;
        }
        return a[1] - b[1];
    });

    return stabilizedThis.map((el) => el[0]);
}

function descendingComparator(a, b, orderBy) {
    if (b[orderBy] < a[orderBy]) {
        return -1;
    }
    if (b[orderBy] > a[orderBy]) {
        return 1;
    }
    return 0;
}

function getComparator(order, orderBy) {
    return order === 'desc'
        ? (a, b) => descendingComparator(a, b, orderBy)
        : (a, b) => -descendingComparator(a, b, orderBy);
}

const EnhancedTableToolbar = (props) => {
    const { title, numSelected, handleCheckboxShowUp, handleCheckboxGoAway, useCheckbox, checkbox } = props;
  
    return (
        <Toolbar
            sx={{
                pl: { sm: 2 },
                pr: { xs: 1, sm: 1 },
                ...(numSelected > 0 && {
                    bgcolor: (theme) =>
                    alpha(theme.palette.primary.main, theme.palette.action.activatedOpacity),
                }),
            }}
        >
            {numSelected > 0 ? (
                <Typography
                    sx={{ flex: '1 1 100%' }}
                    color="inherit"
                    variant="subtitle1"
                    component="div"
                >
                    {numSelected} Seleccionados
                </Typography>
                ) : (
                <Typography
                    sx={{ flex: '1 1 100%' }}
                    variant="h5"
                    id="tableTitle"
                    component="div"
                >
                    {title}
                </Typography>
            )}

            {useCheckbox &&
                <>
                    {numSelected > 0 &&
                        <Tooltip title="Delete">
                            <IconButton>
                                <DeleteIcon />
                            </IconButton>
                        </Tooltip>
                    }
                    {checkbox ? 
                        <Tooltip title="Cancelar">
                        <IconButton onClick={() => handleCheckboxGoAway()}>
                            <CancelIcon />
                        </IconButton>
                    </Tooltip> :
                        <Tooltip title="Multiselección">
                            <IconButton onClick={() => handleCheckboxShowUp()}>
                                <FilterListIcon />
                            </IconButton>
                        </Tooltip>
                    }
                </>
            }
        </Toolbar>
    );
}

const useTableComponent = ({ rows, columns }) => {
    const [page, setPage] = useState(0);
    const [rowsPerPage, setRowsPerPage] = useState(10)
    const [checkboxAction, setCheckboxAction] = useState(false);
    const [order, setOrder] = useState([]);
    const [orderBy, setOrderBy] = useState([]);
    const [selected, setSelected] = useState([]);
    const [data, dispatch] = useReducer(reducer, {
        rowCount: rows.length,
        numSelected: 0
    })

    const handleChangePage = (event, newPage) => {
        setPage(newPage);
    };

    const handleChangeRowsPerPage = (event) => {
        setRowsPerPage(parseInt(event.target.value, 10));
        setPage(0);
    };

    const handleCheckboxShowUp = () => {
        setCheckboxAction(true);
    }

    const handleCheckboxGoAway = () => {
        setSelected([]);
        setCheckboxAction(false);
    }

    const handleSelectAll = (event) => {
        if (event.target.checked) {
            const newSelected = rows.map((n) => n.id);
            setSelected(newSelected);
            return;
        }

        setSelected([]);
    }

    const handleClick = (event, id) => {
        const selectedIndex = selected.indexOf(id);
        let newSelected = [];

        if (selectedIndex === -1) {
            newSelected = newSelected.concat(selected, id);
        } else if (selectedIndex === 0) {
            newSelected = newSelected.concat(selected.slice(1));
        } else if (selectedIndex === selected.length - 1) {
            newSelected = newSelected.concat(selected.slice(0, -1));
        } else if (selectedIndex > 0) {
            newSelected = newSelected.concat(
                selected.slice(0, selectedIndex),
                selected.slice(selectedIndex + 1),
            );
        }

        setSelected(newSelected);
    }

    const isSelected = useCallback((id) => selected.indexOf(id) !== -1);

    const visibleRows = useMemo(
        () =>
          stableSort(rows, getComparator(order, orderBy)).slice(
            page * rowsPerPage,
            page * rowsPerPage + rowsPerPage,
          ),
        [order, orderBy, page, rowsPerPage],
    );

    return {
        page,
        rowsPerPage,
        checkboxAction,
        selected,
        order,
        orderBy,
        data,
        handleCheckboxShowUp,
        handleCheckboxGoAway,
        handleSelectAll,
        handleChangeRowsPerPage,
        handleChangePage,
        handleClick,
        visibleRows,
        isSelected
    };
}

export default function TableComponent (props) {
    const { title, columns, rows, useCheckbox, minWidth, Actions, doubleClick } = props;
    const { page, rowsPerPage, checkboxAction, data, selected,
            handleCheckboxShowUp, handleSelectAll, handleChangeRowsPerPage, 
            handleChangePage, handleCheckboxGoAway, handleClick, visibleRows, 
            isSelected
        } = useTableComponent({ rows, columns });

    return (
        <Paper sx={{...global.border, width: '100%', overflow: 'hidden'}}>
            <Box sx={{margin: 2}}>
                <EnhancedTableToolbar 
                    title={title}
                    handleCheckboxShowUp={handleCheckboxShowUp}
                    handleCheckboxGoAway={handleCheckboxGoAway}
                    numSelected={selected.length}
                    useCheckbox={useCheckbox}
                    checkbox={checkboxAction}
                />
                <TableContainer sx={{ maxHeight: 1000 }}>
                    <Table stickyHeader aria-label="sticky table" >
                        <TableHead>
                            <TableRow>
                                {checkboxAction &&
                                    <TableCell 
                                        padding="checkbox">
                                        <Checkbox
                                            color="primary"
                                            indeterminate={data.numSelected > 0 && data.selected.length < data.rowCount}
                                            checked={data.rowCount > 0 && data.numSelected === data.rowCount}
                                            onChange={handleSelectAll}
                                            inputProps={{
                                                'aria-label': 'select all desserts',
                                            }}
                                        /> 
                                    </TableCell>
                                }
                                {columns.map((column) => (
                                    <TableCell
                                        key={column.id}
                                        style={{ minWidth: minWidth }}
                                        sx={{textAlign: column.id == 'actions' ? 'center' : 'left' }}
                                    >
                                        <Typography variant='subtitle1' sx={{fontWeight: '700'}}>
                                            {column.label}
                                        </Typography>
                                        {column.additional &&
                                            <><br/>{column.additional}</>
                                        }
                                    </TableCell>
                                ))}
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {visibleRows.map((row, index) => {
                                const isItemSelected = isSelected(row.id);
                                const labelId = `enhanced-table-checkbox-${index}`;

                                return (
                                    <TableRow 
                                        key={row.id}
                                        hover
                                        onClick={(event) => {
                                            if(checkboxAction)
                                                handleClick(event, row.id)}
                                        }
                                        onDoubleClick={() => {
                                            if(!checkboxAction)
                                                doubleClick(row.id)
                                        }}
                                        role="checkbox" 
                                        tabIndex={-1} 
                                        aria-checked={isItemSelected}
                                        selected={isItemSelected}
                                    >   
                                        {checkboxAction &&
                                            <TableCell 
                                                padding="checkbox"
                                            >
                                                <Checkbox
                                                    color="primary"
                                                    checked={isItemSelected}
                                                    inputProps={{
                                                        'aria-labelledby': labelId,
                                                    }}
                                                />
                                            </TableCell>
                                        }
                                        {columns.map((column) => {
                                            const key = column.id;

                                            if(key !== 'actions') {
                                                const value = row[column.id];

                                                if (Array.isArray(value)) {
                                                    return (
                                                        <TableCell key={key}>
                                                            {value.join(' - ')}
                                                        </TableCell>
                                                    );
                                                } else if (typeof value == 'object') {
                                                    return (
                                                        <TableCell key={key}>
                                                            <TableCellUsers 
                                                                index={key}
                                                                row={value}
                                                            />
                                                        </TableCell>
                                                    );
                                                } else {
                                                    return (
                                                        <TableCell key={key}>
                                                            {value}
                                                        </TableCell>
                                                    );
                                                }
                                            }
                                            else {
                                                return (
                                                    <TableCell 
                                                        key={key}
                                                        sx={{textAlign: 'center'}}
                                                    >
                                                        <Actions
                                                            id={row.id}
                                                            value={row}
                                                            status={row.status}
                                                        />
                                                    </TableCell>
                                                );
                                            }                                        
                                        })}
                                    </TableRow>
                                );
                            })}
                        </TableBody>
                    </Table>
                </TableContainer>
                <TablePagination
                    rowsPerPageOptions={[10, 25, 100]}
                    component="div"
                    count={rows.length}
                    rowsPerPage={rowsPerPage}
                    page={page}
                    onPageChange={handleChangePage}
                    onRowsPerPageChange={handleChangeRowsPerPage}
                    labelRowsPerPage='Filas por pagina'
                />
            </Box>
        </Paper>
    );
}